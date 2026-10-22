package com.example.courseregistration.concurrency;

import com.example.courseregistration.domain.CourseOffering;
import com.example.courseregistration.domain.DropResult;
import com.example.courseregistration.domain.EnrollmentResult;
import com.example.courseregistration.domain.Learner;
import com.example.courseregistration.repository.CourseOfferingRepository;
import com.example.courseregistration.repository.LearnerRepository;
import com.example.courseregistration.service.CourseRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CourseRegistration Concurrency & Race-Condition Invariant Tests")
class CourseRegistrationConcurrencyTest {

    private CourseOfferingRepository offeringRepository;
    private LearnerRepository learnerRepository;
    private CourseRegistrationService service;

    private static final String COURSE_ID = "CONCURRENCY-101";
    private static final String PREREQ = "CS-100";
    private static final int CAPACITY = 5;

    @BeforeEach
    void setUp() {
        offeringRepository = new CourseOfferingRepository();
        learnerRepository = new LearnerRepository();
        service = new CourseRegistrationService(offeringRepository, learnerRepository);

        CourseOffering offering = new CourseOffering(COURSE_ID, "CS101", "Concurrent CS", CAPACITY, Set.of(PREREQ));
        offeringRepository.save(offering);
    }

    @Test
    @DisplayName("High concurrency: 50 threads competing for 5 seats -> exactly 5 enrolled, exactly 45 waitlisted")
    void shouldMaintainCapacityInvariantsUnderHighConcurrentEnrollment() throws InterruptedException {
        int totalLearners = 50;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalLearners);

        List<EnrollmentResult> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger enrolledCounter = new AtomicInteger();
        AtomicInteger waitlistedCounter = new AtomicInteger();

        // Seed 50 eligible learners
        for (int i = 1; i <= totalLearners; i++) {
            String learnerId = "learner-" + i;
            learnerRepository.save(new Learner(learnerId, Set.of(PREREQ)));
        }

        // Launch 50 concurrent requests
        for (int i = 1; i <= totalLearners; i++) {
            final String learnerId = "learner-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    EnrollmentResult result = service.enroll(COURSE_ID, learnerId);
                    results.add(result);
                    if (result.isEnrolled()) {
                        enrolledCounter.incrementAndGet();
                    } else if (result.isWaitlisted()) {
                        waitlistedCounter.incrementAndGet();
                    }
                } catch (Exception e) {
                    // unexpected
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(enrolledCounter.get()).isEqualTo(CAPACITY);
        assertThat(waitlistedCounter.get()).isEqualTo(totalLearners - CAPACITY);

        CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();
        assertThat(offering.getEnrolledCount()).isEqualTo(CAPACITY);
        assertThat(offering.getWaitlistCount()).isEqualTo(totalLearners - CAPACITY);
        assertThat(offering.getAvailableCapacity()).isEqualTo(0);

        // Verify mutual exclusivity: Enrolled ∩ Waitlist == ∅
        Set<String> enrolled = offering.getEnrolledLearners();
        assertThat(offering.getWaitlist())
                .extracting(entry -> entry.learnerId())
                .doesNotContainAnyElementsOf(enrolled);
    }

    @Test
    @DisplayName("Concurrent drops and promotions: Multiple threads dropping simultaneously maintain exact capacity")
    void shouldMaintainInvariantsDuringConcurrentDropsAndPromotions() throws InterruptedException {
        int totalLearners = 10;
        // Seed 10 learners (5 will be enrolled, 5 waitlisted)
        for (int i = 1; i <= totalLearners; i++) {
            String learnerId = "student-" + i;
            learnerRepository.save(new Learner(learnerId, Set.of(PREREQ)));
            service.enroll(COURSE_ID, learnerId);
        }

        CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();
        assertThat(offering.getEnrolledCount()).isEqualTo(CAPACITY);
        assertThat(offering.getWaitlistCount()).isEqualTo(5);

        // Concurrently drop 3 enrolled students
        List<String> studentsToDrop = List.of("student-1", "student-2", "student-3");
        int dropThreads = studentsToDrop.size();

        ExecutorService executor = Executors.newFixedThreadPool(dropThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(dropThreads);
        List<DropResult> dropResults = Collections.synchronizedList(new ArrayList<>());

        for (String learnerId : studentsToDrop) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    DropResult result = service.drop(COURSE_ID, learnerId);
                    dropResults.add(result);
                } catch (Exception e) {
                    // unexpected
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(dropResults).hasSize(3);
        assertThat(dropResults).allMatch(DropResult::isSuccess);
        assertThat(dropResults).allMatch(DropResult::hasPromotion);

        // Exactly 3 waitlisted students (student-6, student-7, student-8) should have been promoted
        assertThat(offering.getEnrolledCount()).isEqualTo(CAPACITY);
        assertThat(offering.getWaitlistCount()).isEqualTo(2); // 5 - 3 = 2 remaining on waitlist
        assertThat(offering.getAvailableCapacity()).isEqualTo(0);

        // Dropped students must no longer be enrolled
        for (String droppedId : studentsToDrop) {
            assertThat(offering.isEnrolled(droppedId)).isFalse();
        }

        // Remaining waitlist must preserve relative FIFO order (student-9 at pos 1, student-10 at pos 2)
        assertThat(offering.getWaitlistPosition("student-9")).hasValue(1);
        assertThat(offering.getWaitlistPosition("student-10")).hasValue(2);
    }

    @Test
    @DisplayName("Focused Final Seat Contention: 25 threads compete for exactly 1 final seat -> 1 winner enrolled, 24 waitlisted in order")
    void shouldAllowExactlyOneWinnerWhenMultipleThreadsCompeteForFinalSeat() throws InterruptedException {
        String finalSeatOfferingId = "FINAL-SEAT-101";
        CourseOffering singleSeatOffering = new CourseOffering(finalSeatOfferingId, "CS-FINAL", "Final Seat Only", 1, Set.of(PREREQ));
        offeringRepository.save(singleSeatOffering);

        int contenderCount = 25;
        for (int i = 1; i <= contenderCount; i++) {
            learnerRepository.save(new Learner("contender-" + i, Set.of(PREREQ)));
        }

        ExecutorService executor = Executors.newFixedThreadPool(contenderCount);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(contenderCount);

        List<EnrollmentResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 1; i <= contenderCount; i++) {
            final String learnerId = "contender-" + i;
            executor.submit(() -> {
                try {
                    startGun.await();
                    EnrollmentResult result = service.enroll(finalSeatOfferingId, learnerId);
                    results.add(result);
                } catch (Exception e) {
                    // unexpected
                } finally {
                    finishLine.countDown();
                }
            });
        }

        startGun.countDown();
        boolean finished = finishLine.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(results).hasSize(contenderCount);

        // Invariant 1: Exactly 1 enrolled winner
        long enrolledCount = results.stream().filter(EnrollmentResult::isEnrolled).count();
        assertThat(enrolledCount).isEqualTo(1);

        // Invariant 2: Exactly 24 waitlisted learners
        long waitlistedCount = results.stream().filter(EnrollmentResult::isWaitlisted).count();
        assertThat(waitlistedCount).isEqualTo(contenderCount - 1);

        // Invariant 3: Waitlist positions span strictly 1 to 24 without gaps or duplicates
        List<Integer> positions = results.stream()
                .filter(EnrollmentResult::isWaitlisted)
                .map(EnrollmentResult::waitlistPosition)
                .sorted()
                .toList();
        List<Integer> expectedPositions = java.util.stream.IntStream.rangeClosed(1, contenderCount - 1).boxed().toList();
        assertThat(positions).isEqualTo(expectedPositions);

        // Invariant 4: Offering capacity invariant (1 enrolled, 0 available, 24 waitlisted)
        CourseOffering verifiedOffering = offeringRepository.findById(finalSeatOfferingId).orElseThrow();
        assertThat(verifiedOffering.getEnrolledCount()).isEqualTo(1);
        assertThat(verifiedOffering.getAvailableCapacity()).isEqualTo(0);
        assertThat(verifiedOffering.getWaitlistCount()).isEqualTo(contenderCount - 1);

        // Invariant 5: Mutual exclusivity: Enrolled ∩ Waitlist == ∅
        Set<String> enrolled = verifiedOffering.getEnrolledLearners();
        assertThat(verifiedOffering.getWaitlist())
                .extracting(entry -> entry.learnerId())
                .doesNotContainAnyElementsOf(enrolled);
    }
}
