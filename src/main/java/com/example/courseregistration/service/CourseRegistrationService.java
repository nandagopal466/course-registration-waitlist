package com.example.courseregistration.service;

import com.example.courseregistration.domain.CourseOffering;
import com.example.courseregistration.domain.DropResult;
import com.example.courseregistration.domain.DropStatus;
import com.example.courseregistration.domain.EnrollmentResult;
import com.example.courseregistration.domain.EnrollmentStatus;
import com.example.courseregistration.domain.Learner;
import com.example.courseregistration.domain.exception.CourseNotFoundException;
import com.example.courseregistration.domain.exception.DuplicateEnrollmentException;
import com.example.courseregistration.domain.exception.LearnerNotFoundException;
import com.example.courseregistration.domain.exception.PrerequisiteNotMetException;
import com.example.courseregistration.repository.CourseOfferingRepository;
import com.example.courseregistration.repository.LearnerRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Service orchestrating course enrollment business logic.
 * Enforces course offering and learner existence, evaluates prerequisite eligibility,
 * manages capacity/waitlisting, and prevents duplicate records.
 */
@Service
public class CourseRegistrationService {

    private final CourseOfferingRepository courseOfferingRepository;
    private final LearnerRepository learnerRepository;

    public CourseRegistrationService(CourseOfferingRepository courseOfferingRepository,
                                     LearnerRepository learnerRepository) {
        this.courseOfferingRepository = Objects.requireNonNull(courseOfferingRepository, "courseOfferingRepository must not be null");
        this.learnerRepository = Objects.requireNonNull(learnerRepository, "learnerRepository must not be null");
    }

    /**
     * Enrolls an existing learner in a course offering.
     * Evaluates prerequisite eligibility from the learner's profile in LearnerRepository.
     *
     * @param offeringId unique identifier of the course offering
     * @param learnerId unique identifier of the learner
     * @return EnrollmentResult indicating ENROLLED, WAITLISTED, or documented failure
     */
    public EnrollmentResult enroll(String offeringId, String learnerId) {
        if (offeringId == null || offeringId.isBlank()) {
            return EnrollmentResult.failure(
                    learnerId != null ? learnerId : "UNKNOWN",
                    EnrollmentStatus.INVALID_REQUEST,
                    "Course offering ID must not be blank."
            );
        }

        if (learnerId == null || learnerId.isBlank()) {
            return EnrollmentResult.failure(
                    "UNKNOWN",
                    EnrollmentStatus.INVALID_REQUEST,
                    "Learner ID must not be blank."
            );
        }

        String normalizedOfferingId = offeringId.trim();
        String normalizedLearnerId = learnerId.trim();

        Optional<CourseOffering> offeringOpt = courseOfferingRepository.findById(normalizedOfferingId);
        if (offeringOpt.isEmpty()) {
            return EnrollmentResult.failure(
                    normalizedLearnerId,
                    EnrollmentStatus.OFFERING_NOT_FOUND,
                    "Course offering not found: " + normalizedOfferingId
            );
        }

        Optional<Learner> learnerOpt = learnerRepository.findById(normalizedLearnerId);
        if (learnerOpt.isEmpty()) {
            return EnrollmentResult.failure(
                    normalizedLearnerId,
                    EnrollmentStatus.LEARNER_NOT_FOUND,
                    "Learner not found: " + normalizedLearnerId
            );
        }

        CourseOffering offering = offeringOpt.get();
        Learner learner = learnerOpt.get();

        return executeEnrollment(offering, learner.learnerId(), learner.completedCourses());
    }

    /**
     * Enrolls a learner in a course offering with explicitly provided completed prerequisites.
     * If learner does not yet exist in LearnerRepository, registers them automatically.
     *
     * @param offeringId unique identifier of the course offering
     * @param learnerId unique identifier of the learner
     * @param completedCourses set of completed course codes for prerequisite evaluation
     * @return EnrollmentResult indicating ENROLLED, WAITLISTED, or documented failure
     */
    public EnrollmentResult enroll(String offeringId, String learnerId, Set<String> completedCourses) {
        if (completedCourses == null) {
            return enroll(offeringId, learnerId);
        }

        if (offeringId == null || offeringId.isBlank()) {
            return EnrollmentResult.failure(
                    learnerId != null ? learnerId : "UNKNOWN",
                    EnrollmentStatus.INVALID_REQUEST,
                    "Course offering ID must not be blank."
            );
        }

        if (learnerId == null || learnerId.isBlank()) {
            return EnrollmentResult.failure(
                    "UNKNOWN",
                    EnrollmentStatus.INVALID_REQUEST,
                    "Learner ID must not be blank."
            );
        }

        String normalizedOfferingId = offeringId.trim();
        String normalizedLearnerId = learnerId.trim();

        Optional<CourseOffering> offeringOpt = courseOfferingRepository.findById(normalizedOfferingId);
        if (offeringOpt.isEmpty()) {
            return EnrollmentResult.failure(
                    normalizedLearnerId,
                    EnrollmentStatus.OFFERING_NOT_FOUND,
                    "Course offering not found: " + normalizedOfferingId
            );
        }

        // Ensure learner profile exists or update it
        Learner learner = learnerRepository.findById(normalizedLearnerId)
                .map(existing -> {
                    if (!existing.completedCourses().equals(completedCourses)) {
                        return learnerRepository.save(new Learner(existing.learnerId(), existing.name(), completedCourses));
                    }
                    return existing;
                })
                .orElseGet(() -> learnerRepository.save(new Learner(normalizedLearnerId, completedCourses)));

        return executeEnrollment(offeringOpt.get(), learner.learnerId(), learner.completedCourses());
    }

    /**
     * Enrolls an existing learner, throwing domain exceptions if the request fails.
     */
    public EnrollmentResult enrollOrThrow(String offeringId, String learnerId) {
        if (offeringId == null || offeringId.isBlank()) {
            throw new IllegalArgumentException("Course offering ID must not be blank.");
        }
        if (learnerId == null || learnerId.isBlank()) {
            throw new IllegalArgumentException("Learner ID must not be blank.");
        }

        String normalizedOfferingId = offeringId.trim();
        String normalizedLearnerId = learnerId.trim();

        CourseOffering offering = courseOfferingRepository.findById(normalizedOfferingId)
                .orElseThrow(() -> new CourseNotFoundException(normalizedOfferingId));

        Learner learner = learnerRepository.findById(normalizedLearnerId)
                .orElseThrow(() -> new LearnerNotFoundException(normalizedLearnerId));

        return offering.enroll(learner.learnerId(), learner.completedCourses());
    }

    /**
     * Enrolls a learner, optionally registering or updating completed courses,
     * throwing domain exceptions if the request fails.
     */
    public EnrollmentResult enrollOrThrow(String offeringId, String learnerId, Set<String> completedCourses) {
        if (completedCourses == null) {
            return enrollOrThrow(offeringId, learnerId);
        }

        if (offeringId == null || offeringId.isBlank()) {
            throw new IllegalArgumentException("Course offering ID must not be blank.");
        }
        if (learnerId == null || learnerId.isBlank()) {
            throw new IllegalArgumentException("Learner ID must not be blank.");
        }

        String normalizedOfferingId = offeringId.trim();
        String normalizedLearnerId = learnerId.trim();

        CourseOffering offering = courseOfferingRepository.findById(normalizedOfferingId)
                .orElseThrow(() -> new CourseNotFoundException(normalizedOfferingId));

        Learner learner = learnerRepository.findById(normalizedLearnerId)
                .map(existing -> {
                    if (!existing.completedCourses().equals(completedCourses)) {
                        return learnerRepository.save(new Learner(existing.learnerId(), existing.name(), completedCourses));
                    }
                    return existing;
                })
                .orElseGet(() -> learnerRepository.save(new Learner(normalizedLearnerId, completedCourses)));

        return offering.enroll(learner.learnerId(), learner.completedCourses());
    }

    private EnrollmentResult executeEnrollment(CourseOffering offering, String learnerId, Set<String> completedCourses) {
        try {
            return offering.enroll(learnerId, completedCourses);
        } catch (DuplicateEnrollmentException e) {
            EnrollmentStatus status = e.isAlreadyWaitlisted()
                    ? EnrollmentStatus.ALREADY_WAITLISTED
                    : EnrollmentStatus.ALREADY_ENROLLED;
            return EnrollmentResult.failure(learnerId, status, e.getMessage());
        } catch (PrerequisiteNotMetException e) {
            return EnrollmentResult.failure(learnerId, EnrollmentStatus.PREREQUISITE_NOT_MET, e.getMessage());
        } catch (IllegalArgumentException e) {
            return EnrollmentResult.failure(learnerId, EnrollmentStatus.INVALID_REQUEST, e.getMessage());
        }
    }

    public Optional<CourseOffering> getOffering(String offeringId) {
        return courseOfferingRepository.findById(offeringId);
    }

    public Optional<Learner> getLearner(String learnerId) {
        return learnerRepository.findById(learnerId);
    }

    public Learner registerLearner(String learnerId, Set<String> completedCourses) {
        return learnerRepository.save(new Learner(learnerId, completedCourses));
    }

    /**
     * Drops an enrolled learner from a course offering and triggers FIFO waitlist promotion.
     *
     * @param offeringId unique identifier of the course offering
     * @param learnerId unique identifier of the learner to drop
     * @return DropResult indicating DROPPED (with promoted learner ID if promoted), or documented failure
     */
    public DropResult drop(String offeringId, String learnerId) {
        if (offeringId == null || offeringId.isBlank()) {
            return DropResult.failure(
                    learnerId != null ? learnerId : "UNKNOWN",
                    com.example.courseregistration.domain.DropStatus.INVALID_REQUEST,
                    "Course offering ID must not be blank."
            );
        }

        if (learnerId == null || learnerId.isBlank()) {
            return DropResult.failure(
                    "UNKNOWN",
                    com.example.courseregistration.domain.DropStatus.INVALID_REQUEST,
                    "Learner ID must not be blank."
            );
        }

        String normalizedOfferingId = offeringId.trim();
        String normalizedLearnerId = learnerId.trim();

        Optional<CourseOffering> offeringOpt = courseOfferingRepository.findById(normalizedOfferingId);
        if (offeringOpt.isEmpty()) {
            return DropResult.failure(
                    normalizedLearnerId,
                    com.example.courseregistration.domain.DropStatus.OFFERING_NOT_FOUND,
                    "Course offering not found: " + normalizedOfferingId
            );
        }

        Optional<Learner> learnerOpt = learnerRepository.findById(normalizedLearnerId);
        if (learnerOpt.isEmpty()) {
            return DropResult.failure(
                    normalizedLearnerId,
                    com.example.courseregistration.domain.DropStatus.LEARNER_NOT_FOUND,
                    "Learner not found: " + normalizedLearnerId
            );
        }

        CourseOffering offering = offeringOpt.get();

        try {
            return offering.drop(normalizedLearnerId, entry -> {
                Set<String> latestCompleted = learnerRepository.findById(entry.learnerId())
                        .map(Learner::completedCourses)
                        .orElse(entry.completedCourses());
                return offering.isEligible(latestCompleted);
            });
        } catch (com.example.courseregistration.domain.exception.NotEnrolledException e) {
            return DropResult.failure(normalizedLearnerId, com.example.courseregistration.domain.DropStatus.NOT_ENROLLED, e.getMessage());
        } catch (IllegalArgumentException e) {
            return DropResult.failure(normalizedLearnerId, com.example.courseregistration.domain.DropStatus.INVALID_REQUEST, e.getMessage());
        }
    }

    /**
     * Drops an enrolled learner, throwing domain exceptions on invalid requests.
     */
    public DropResult dropOrThrow(String offeringId, String learnerId) {
        if (offeringId == null || offeringId.isBlank()) {
            throw new IllegalArgumentException("Course offering ID must not be blank.");
        }
        if (learnerId == null || learnerId.isBlank()) {
            throw new IllegalArgumentException("Learner ID must not be blank.");
        }

        String normalizedOfferingId = offeringId.trim();
        String normalizedLearnerId = learnerId.trim();

        CourseOffering offering = courseOfferingRepository.findById(normalizedOfferingId)
                .orElseThrow(() -> new CourseNotFoundException(normalizedOfferingId));

        if (!learnerRepository.existsById(normalizedLearnerId)) {
            throw new LearnerNotFoundException(normalizedLearnerId);
        }

        return offering.drop(normalizedLearnerId, entry -> {
            Set<String> latestCompleted = learnerRepository.findById(entry.learnerId())
                    .map(Learner::completedCourses)
                    .orElse(entry.completedCourses());
            return offering.isEligible(latestCompleted);
        });
    }
}
