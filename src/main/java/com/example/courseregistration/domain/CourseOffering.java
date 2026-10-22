package com.example.courseregistration.domain;

import com.example.courseregistration.domain.exception.DuplicateEnrollmentException;
import com.example.courseregistration.domain.exception.InvalidCapacityException;
import com.example.courseregistration.domain.exception.NotEnrolledException;
import com.example.courseregistration.domain.exception.PrerequisiteNotMetException;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * CourseOffering aggregate root representing a single course offering.
 * Encapsulates capacity limits, prerequisite verification, enrollment membership,
 * and FIFO waitlist promotion under the "Skip and Retain" policy.
 */
public class CourseOffering {

    private final String courseId;
    private final String courseCode;
    private final String title;
    private final int capacity;
    private final Set<String> prerequisites;

    private final LinkedHashSet<String> enrolledLearners = new LinkedHashSet<>();
    private final LinkedList<WaitlistEntry> waitlist = new LinkedList<>();

    public CourseOffering(String courseId, String courseCode, String title, int capacity, Set<String> prerequisites) {
        Objects.requireNonNull(courseId, "courseId must not be null");
        Objects.requireNonNull(courseCode, "courseCode must not be null");
        Objects.requireNonNull(title, "title must not be null");

        if (courseId.isBlank()) {
            throw new IllegalArgumentException("courseId must not be blank");
        }
        if (courseCode.isBlank()) {
            throw new IllegalArgumentException("courseCode must not be blank");
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (capacity <= 0) {
            throw new InvalidCapacityException("Course capacity must be strictly greater than zero, given: " + capacity);
        }

        this.courseId = courseId.trim();
        this.courseCode = courseCode.trim();
        this.title = title.trim();
        this.capacity = capacity;
        this.prerequisites = prerequisites == null ? Set.of() : Set.copyOf(prerequisites);
    }

    public String getCourseId() {
        return courseId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getTitle() {
        return title;
    }

    public int getCapacity() {
        return capacity;
    }

    public Set<String> getPrerequisites() {
        return prerequisites;
    }

    // --- Derived State Inspection Methods ---

    public synchronized Set<String> getEnrolledLearners() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(enrolledLearners));
    }

    public synchronized List<WaitlistEntry> getWaitlist() {
        return List.copyOf(waitlist);
    }

    public synchronized int getEnrolledCount() {
        return enrolledLearners.size();
    }

    public synchronized int getWaitlistCount() {
        return waitlist.size();
    }

    public synchronized int getAvailableCapacity() {
        return Math.max(0, capacity - enrolledLearners.size());
    }

    public synchronized boolean isFull() {
        return enrolledLearners.size() >= capacity;
    }

    public synchronized boolean isEnrolled(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            return false;
        }
        return enrolledLearners.contains(learnerId.trim());
    }

    public synchronized boolean isWaitlisted(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            return false;
        }
        String normalizedId = learnerId.trim();
        for (WaitlistEntry entry : waitlist) {
            if (entry.learnerId().equals(normalizedId)) {
                return true;
            }
        }
        return false;
    }

    public synchronized OptionalInt getWaitlistPosition(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            return OptionalInt.empty();
        }
        String normalizedId = learnerId.trim();
        int position = 1;
        for (WaitlistEntry entry : waitlist) {
            if (entry.learnerId().equals(normalizedId)) {
                return OptionalInt.of(position);
            }
            position++;
        }
        return OptionalInt.empty();
    }

    /**
     * Checks if learner's completed courses satisfy all course offering prerequisites.
     */
    public boolean isEligible(Set<String> completedCourses) {
        if (prerequisites.isEmpty()) {
            return true;
        }
        if (completedCourses == null) {
            return false;
        }
        return completedCourses.containsAll(prerequisites);
    }

    // --- Mutating Operations ---

    /**
     * Attempts to enroll a learner.
     * If seats are available, enrolls directly.
     * If capacity is reached, places the learner on the FIFO waitlist.
     *
     * @param learnerId unique identifier of learner
     * @param completedCourses set of course codes completed by learner
     * @return EnrollmentResult indicating whether enrolled or waitlisted (with position)
     * @throws DuplicateEnrollmentException if learner is already enrolled or waitlisted
     * @throws PrerequisiteNotMetException if learner does not satisfy prerequisites
     */
    public synchronized EnrollmentResult enroll(String learnerId, Set<String> completedCourses) {
        Objects.requireNonNull(learnerId, "learnerId must not be null");
        if (learnerId.isBlank()) {
            throw new IllegalArgumentException("learnerId must not be blank");
        }

        String normalizedId = learnerId.trim();

        // Rule 3 & 4: No duplicate enrollment or waitlist entries
        if (enrolledLearners.contains(normalizedId)) {
            throw new DuplicateEnrollmentException(normalizedId, false);
        }
        if (isWaitlisted(normalizedId)) {
            throw new DuplicateEnrollmentException(normalizedId, true);
        }

        // Rule 1 & 7: Prerequisites must be satisfied to enroll or waitlist
        Set<String> completed = completedCourses == null ? Set.of() : completedCourses;
        if (!isEligible(completed)) {
            throw new PrerequisiteNotMetException(normalizedId, prerequisites, completed);
        }

        // Rule 1: Enroll immediately if capacity remains
        if (enrolledLearners.size() < capacity) {
            enrolledLearners.add(normalizedId);
            return EnrollmentResult.enrolled(normalizedId);
        }

        // Rule 2: When full, eligible learner joins FIFO waitlist
        WaitlistEntry entry = new WaitlistEntry(normalizedId, completed);
        waitlist.addLast(entry);
        return EnrollmentResult.waitlisted(normalizedId, waitlist.size());
    }

    /**
     * Drops an enrolled learner and attempts to promote the earliest eligible waitlisted learner.
     *
     * @param learnerId unique identifier of learner to drop
     * @return DropResult containing dropped learner ID, promoted learner ID (if any), and available capacity
     * @throws NotEnrolledException if learner is not currently enrolled
     */
    public synchronized DropResult drop(String learnerId) {
        return drop(learnerId, entry -> isEligible(entry.completedCourses()));
    }

    /**
     * Drops an enrolled learner and attempts to promote the earliest eligible waitlisted learner.
     *
     * @param learnerId unique identifier of learner to drop
     * @param eligibilityChecker predicate used to evaluate waitlisted candidate eligibility
     * @return DropResult containing dropped learner ID, promoted learner ID (if any), and available capacity
     * @throws NotEnrolledException if learner is not currently enrolled
     */
    public synchronized DropResult drop(String learnerId, java.util.function.Predicate<WaitlistEntry> eligibilityChecker) {
        Objects.requireNonNull(learnerId, "learnerId must not be null");
        if (learnerId.isBlank()) {
            throw new IllegalArgumentException("learnerId must not be blank");
        }

        String normalizedId = learnerId.trim();

        // Rule 5: A learner can drop only when currently enrolled
        if (!enrolledLearners.contains(normalizedId)) {
            throw new NotEnrolledException(normalizedId);
        }

        // Rule 6: Dropping frees a seat
        enrolledLearners.remove(normalizedId);

        // Rule 6, 7, 8, 9, 10: Attempt to promote the earliest eligible learner
        Optional<String> promotedLearnerId = Optional.empty();
        Iterator<WaitlistEntry> iterator = waitlist.iterator();

        java.util.function.Predicate<WaitlistEntry> checker =
                eligibilityChecker != null ? eligibilityChecker : entry -> isEligible(entry.completedCourses());

        while (iterator.hasNext()) {
            WaitlistEntry entry = iterator.next();
            if (checker.test(entry)) {
                // Rule 6: Earliest eligible learner is promoted
                iterator.remove();
                enrolledLearners.add(entry.learnerId());
                promotedLearnerId = Optional.of(entry.learnerId());
                break;
            }
            // Rule 8 & 9: Ineligible learner is skipped but retained in the queue; continue search
        }

        // Rule 10: If no eligible learner exists, seat remains vacant
        String message = promotedLearnerId
                .map(id -> "Learner '" + normalizedId + "' dropped. Promoted learner '" + id + "' from waitlist.")
                .orElse("Learner '" + normalizedId + "' dropped. Seat remains vacant.");

        return DropResult.dropped(normalizedId, promotedLearnerId, getAvailableCapacity(), message);
    }

    /**
     * Helper used in tests to construct specific waitlist scenarios
     * (e.g. testing skip-and-retain when an entry becomes ineligible).
     */
    public synchronized void addToWaitlistForTesting(String learnerId, Set<String> completedCourses) {
        waitlist.addLast(new WaitlistEntry(learnerId, completedCourses));
    }
}
