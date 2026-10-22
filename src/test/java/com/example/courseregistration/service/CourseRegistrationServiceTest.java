package com.example.courseregistration.service;

import com.example.courseregistration.domain.CourseOffering;
import com.example.courseregistration.domain.EnrollmentResult;
import com.example.courseregistration.domain.EnrollmentStatus;
import com.example.courseregistration.domain.Learner;
import com.example.courseregistration.domain.exception.CourseNotFoundException;
import com.example.courseregistration.domain.exception.DuplicateEnrollmentException;
import com.example.courseregistration.domain.exception.LearnerNotFoundException;
import com.example.courseregistration.domain.exception.PrerequisiteNotMetException;
import com.example.courseregistration.repository.CourseOfferingRepository;
import com.example.courseregistration.repository.LearnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CourseRegistrationService Enrollment Tests")
class CourseRegistrationServiceTest {

    private CourseOfferingRepository offeringRepository;
    private LearnerRepository learnerRepository;
    private CourseRegistrationService service;

    private static final String COURSE_ID = "CS-201";
    private static final String PREREQ = "CS-101";

    @BeforeEach
    void setUp() {
        offeringRepository = new CourseOfferingRepository();
        learnerRepository = new LearnerRepository();
        service = new CourseRegistrationService(offeringRepository, learnerRepository);

        // Seed single course offering with capacity = 2 and required prerequisite = CS-101
        CourseOffering offering = new CourseOffering(COURSE_ID, "CS201", "Algorithms", 2, Set.of(PREREQ));
        offeringRepository.save(offering);
    }

    @Nested
    @DisplayName("Available Capacity & Direct Enrollment")
    class AvailableCapacityTests {

        @Test
        @DisplayName("Requirement 5: Should enroll eligible learner immediately when capacity remains")
        void shouldEnrollEligibleLearnerWhenCapacityAvailable() {
            learnerRepository.save(new Learner("learner-1", Set.of(PREREQ)));

            EnrollmentResult result = service.enroll(COURSE_ID, "learner-1");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isEnrolled()).isTrue();
            assertThat(result.status()).isEqualTo(EnrollmentStatus.ENROLLED);
            assertThat(result.waitlistPosition()).isNull();

            CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();
            assertThat(offering.getEnrolledCount()).isEqualTo(1);
            assertThat(offering.getAvailableCapacity()).isEqualTo(1);
            assertThat(offering.isEnrolled("learner-1")).isTrue();
            assertThat(offering.getWaitlistCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should auto-register learner when completed courses are explicitly provided")
        void shouldEnrollAndAutoRegisterLearnerWhenCoursesProvided() {
            EnrollmentResult result = service.enroll(COURSE_ID, "new-learner", Set.of(PREREQ));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isEnrolled()).isTrue();
            assertThat(result.status()).isEqualTo(EnrollmentStatus.ENROLLED);

            assertThat(learnerRepository.existsById("new-learner")).isTrue();
            CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();
            assertThat(offering.isEnrolled("new-learner")).isTrue();
        }
    }

    @Nested
    @DisplayName("Full Capacity & Waitlisting")
    class FullCapacityTests {

        @Test
        @DisplayName("Requirement 6: When full, should add eligible learner to the FIFO waitlist with position")
        void shouldWaitlistEligibleLearnerWhenOfferingIsFull() {
            // Fill capacity (capacity = 2)
            learnerRepository.save(new Learner("learner-1", Set.of(PREREQ)));
            learnerRepository.save(new Learner("learner-2", Set.of(PREREQ)));
            service.enroll(COURSE_ID, "learner-1");
            service.enroll(COURSE_ID, "learner-2");

            CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();
            assertThat(offering.isFull()).isTrue();

            // Learner-3 enrolls when full -> should join waitlist at pos 1
            learnerRepository.save(new Learner("learner-3", Set.of(PREREQ)));
            EnrollmentResult waitlistResult1 = service.enroll(COURSE_ID, "learner-3");

            assertThat(waitlistResult1.isSuccess()).isTrue();
            assertThat(waitlistResult1.isWaitlisted()).isTrue();
            assertThat(waitlistResult1.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
            assertThat(waitlistResult1.waitlistPosition()).isEqualTo(1);

            // Learner-4 enrolls when full -> should join waitlist at pos 2
            learnerRepository.save(new Learner("learner-4", Set.of(PREREQ)));
            EnrollmentResult waitlistResult2 = service.enroll(COURSE_ID, "learner-4");

            assertThat(waitlistResult2.isSuccess()).isTrue();
            assertThat(waitlistResult2.isWaitlisted()).isTrue();
            assertThat(waitlistResult2.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
            assertThat(waitlistResult2.waitlistPosition()).isEqualTo(2);

            // Invariant checks
            assertThat(offering.getEnrolledCount()).isEqualTo(2);
            assertThat(offering.getAvailableCapacity()).isEqualTo(0);
            assertThat(offering.getWaitlistCount()).isEqualTo(2);
            assertThat(offering.isWaitlisted("learner-3")).isTrue();
            assertThat(offering.isWaitlisted("learner-4")).isTrue();
        }
    }

    @Nested
    @DisplayName("Prerequisite Eligibility & Rejection")
    class PrerequisiteTests {

        @Test
        @DisplayName("Requirement 3 & 4: Ineligible learner is rejected with PREREQUISITE_NOT_MET without changing state")
        void shouldRejectIneligibleLearnerWithoutStateChange() {
            // Learner lacks required prerequisite CS-101
            learnerRepository.save(new Learner("ineligible-learner", Set.of("MATH-100")));

            EnrollmentResult result = service.enroll(COURSE_ID, "ineligible-learner");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isFailure()).isTrue();
            assertThat(result.status()).isEqualTo(EnrollmentStatus.PREREQUISITE_NOT_MET);
            assertThat(result.message()).contains("does not satisfy required prerequisites");

            // Verify state is completely unchanged
            CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();
            assertThat(offering.getEnrolledCount()).isEqualTo(0);
            assertThat(offering.getAvailableCapacity()).isEqualTo(2);
            assertThat(offering.getWaitlistCount()).isEqualTo(0);
            assertThat(offering.isEnrolled("ineligible-learner")).isFalse();
            assertThat(offering.isWaitlisted("ineligible-learner")).isFalse();
        }
    }

    @Nested
    @DisplayName("Duplicate Prevention & Idempotency")
    class DuplicatePreventionTests {

        @Test
        @DisplayName("Requirement 7: Prevent duplicate enrollment requests from creating duplicate records")
        void shouldRejectDuplicateEnrollmentRequest() {
            learnerRepository.save(new Learner("learner-1", Set.of(PREREQ)));
            service.enroll(COURSE_ID, "learner-1");

            // Second enrollment request for same learner
            EnrollmentResult duplicateResult = service.enroll(COURSE_ID, "learner-1");

            assertThat(duplicateResult.isSuccess()).isFalse();
            assertThat(duplicateResult.status()).isEqualTo(EnrollmentStatus.ALREADY_ENROLLED);
            assertThat(duplicateResult.message()).contains("already enrolled");

            CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();
            assertThat(offering.getEnrolledCount()).isEqualTo(1);
            assertThat(offering.getAvailableCapacity()).isEqualTo(1);
        }

        @Test
        @DisplayName("Requirement 7: Prevent duplicate waitlist requests from creating duplicate waitlist records")
        void shouldRejectDuplicateWaitlistRequest() {
            // Fill offering capacity
            learnerRepository.save(new Learner("learner-1", Set.of(PREREQ)));
            learnerRepository.save(new Learner("learner-2", Set.of(PREREQ)));
            service.enroll(COURSE_ID, "learner-1");
            service.enroll(COURSE_ID, "learner-2");

            // Waitlist learner-3
            learnerRepository.save(new Learner("learner-3", Set.of(PREREQ)));
            EnrollmentResult waitlistResult = service.enroll(COURSE_ID, "learner-3");
            assertThat(waitlistResult.status()).isEqualTo(EnrollmentStatus.WAITLISTED);

            // Re-attempt waitlist for learner-3
            EnrollmentResult duplicateResult = service.enroll(COURSE_ID, "learner-3");

            assertThat(duplicateResult.isSuccess()).isFalse();
            assertThat(duplicateResult.status()).isEqualTo(EnrollmentStatus.ALREADY_WAITLISTED);
            assertThat(duplicateResult.message()).contains("already waitlisted");

            CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();
            assertThat(offering.getWaitlistCount()).isEqualTo(1);
            assertThat(offering.getWaitlistPosition("learner-3")).hasValue(1);
        }

        @Test
        @DisplayName("Requirement 9: Repeated requests cannot exceed capacity or change queue ordering incorrectly")
        void shouldEnsureRepeatedRequestsPreserveInvariantsAndOrdering() {
            learnerRepository.save(new Learner("learner-1", Set.of(PREREQ)));
            learnerRepository.save(new Learner("learner-2", Set.of(PREREQ)));
            learnerRepository.save(new Learner("learner-3", Set.of(PREREQ)));
            learnerRepository.save(new Learner("learner-4", Set.of(PREREQ)));

            service.enroll(COURSE_ID, "learner-1"); // Enrolled
            service.enroll(COURSE_ID, "learner-2"); // Enrolled (Full: 2/2)
            service.enroll(COURSE_ID, "learner-3"); // Waitlist pos 1
            service.enroll(COURSE_ID, "learner-4"); // Waitlist pos 2

            // Hammer with duplicate requests
            for (int i = 0; i < 5; i++) {
                assertThat(service.enroll(COURSE_ID, "learner-1").status()).isEqualTo(EnrollmentStatus.ALREADY_ENROLLED);
                assertThat(service.enroll(COURSE_ID, "learner-3").status()).isEqualTo(EnrollmentStatus.ALREADY_WAITLISTED);
                assertThat(service.enroll(COURSE_ID, "learner-4").status()).isEqualTo(EnrollmentStatus.ALREADY_WAITLISTED);
            }

            // Assert capacity never exceeded
            CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();
            assertThat(offering.getEnrolledCount()).isEqualTo(2);
            assertThat(offering.getWaitlistCount()).isEqualTo(2);

            // Assert queue ordering unchanged
            assertThat(offering.getWaitlistPosition("learner-3")).hasValue(1);
            assertThat(offering.getWaitlistPosition("learner-4")).hasValue(2);
        }
    }

    @Nested
    @DisplayName("Existence & Validation Failures")
    class ExistenceAndValidationTests {

        @Test
        @DisplayName("Requirement 2: Should return OFFERING_NOT_FOUND when course offering does not exist")
        void shouldReturnOfferingNotFound() {
            learnerRepository.save(new Learner("learner-1", Set.of(PREREQ)));

            EnrollmentResult result = service.enroll("NON-EXISTENT", "learner-1");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.status()).isEqualTo(EnrollmentStatus.OFFERING_NOT_FOUND);
            assertThat(result.message()).contains("Course offering not found");
        }

        @Test
        @DisplayName("Requirement 2: Should return LEARNER_NOT_FOUND when learner profile does not exist")
        void shouldReturnLearnerNotFound() {
            EnrollmentResult result = service.enroll(COURSE_ID, "unknown-learner");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.status()).isEqualTo(EnrollmentStatus.LEARNER_NOT_FOUND);
            assertThat(result.message()).contains("Learner not found");
        }

        @Test
        @DisplayName("Should return INVALID_REQUEST when offeringId or learnerId is blank or null")
        void shouldRejectBlankIdentifiers() {
            assertThat(service.enroll("", "learner-1").status()).isEqualTo(EnrollmentStatus.INVALID_REQUEST);
            assertThat(service.enroll(null, "learner-1").status()).isEqualTo(EnrollmentStatus.INVALID_REQUEST);
            assertThat(service.enroll(COURSE_ID, "").status()).isEqualTo(EnrollmentStatus.INVALID_REQUEST);
            assertThat(service.enroll(COURSE_ID, null).status()).isEqualTo(EnrollmentStatus.INVALID_REQUEST);
        }

        @Test
        @DisplayName("Should support enrollOrThrow for callers expecting domain exceptions")
        void shouldSupportEnrollOrThrowExceptions() {
            learnerRepository.save(new Learner("learner-1", Set.of("WRONG-COURSE")));

            // Prerequisite failure throws exception
            assertThatThrownBy(() -> service.enrollOrThrow(COURSE_ID, "learner-1"))
                    .isInstanceOf(PrerequisiteNotMetException.class);

            // Non-existent course throws exception
            assertThatThrownBy(() -> service.enrollOrThrow("BAD-COURSE", "learner-1"))
                    .isInstanceOf(CourseNotFoundException.class);

            // Non-existent learner throws exception
            assertThatThrownBy(() -> service.enrollOrThrow(COURSE_ID, "ghost-learner"))
                    .isInstanceOf(LearnerNotFoundException.class);

            // Duplicate enrollment throws exception
            learnerRepository.save(new Learner("learner-eligible", Set.of(PREREQ)));
            service.enrollOrThrow(COURSE_ID, "learner-eligible");
            assertThatThrownBy(() -> service.enrollOrThrow(COURSE_ID, "learner-eligible"))
                    .isInstanceOf(DuplicateEnrollmentException.class);
        }
    }

    @Nested
    @DisplayName("Drop & FIFO Waitlist Promotion Workflow")
    class DropAndPromotionWorkflowTests {

        @Test
        @DisplayName("FIFO promotion: Dropping an enrolled learner frees a seat and promotes earliest eligible waitlisted learner")
        void shouldPerformFifoPromotionWhenEnrolledLearnerDrops() {
            learnerRepository.save(new Learner("learner-1", Set.of(PREREQ)));
            learnerRepository.save(new Learner("learner-2", Set.of(PREREQ)));
            learnerRepository.save(new Learner("learner-3", Set.of(PREREQ)));
            learnerRepository.save(new Learner("learner-4", Set.of(PREREQ)));

            service.enroll(COURSE_ID, "learner-1"); // Enrolled
            service.enroll(COURSE_ID, "learner-2"); // Enrolled (Capacity full: 2/2)
            service.enroll(COURSE_ID, "learner-3"); // Waitlist pos 1
            service.enroll(COURSE_ID, "learner-4"); // Waitlist pos 2

            // Action: learner-1 drops
            com.example.courseregistration.domain.DropResult dropResult = service.drop(COURSE_ID, "learner-1");

            assertThat(dropResult.isSuccess()).isTrue();
            assertThat(dropResult.status()).isEqualTo(com.example.courseregistration.domain.DropStatus.DROPPED);
            assertThat(dropResult.droppedLearnerId()).isEqualTo("learner-1");
            assertThat(dropResult.promotedLearnerId()).contains("learner-3");
            assertThat(dropResult.availableCapacity()).isEqualTo(0); // seat immediately taken by promoted learner

            // Verify offering state & invariants
            CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();
            assertThat(offering.isEnrolled("learner-1")).isFalse();
            assertThat(offering.isEnrolled("learner-3")).isTrue();
            assertThat(offering.isWaitlisted("learner-3")).isFalse();
            assertThat(offering.getEnrolledCount()).isEqualTo(2);
            assertThat(offering.getWaitlistCount()).isEqualTo(1);
            assertThat(offering.getWaitlistPosition("learner-4")).hasValue(1);
        }

        @Test
        @DisplayName("Rule 8 & 9: Ineligible learner at head of waitlist is skipped and retained; later eligible learner is promoted")
        void shouldSkipIneligibleWaitlistedLearnerAndPromoteLaterEligible() {
            CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();

            // Enroll learner-1 and learner-2 to fill capacity (2/2)
            learnerRepository.save(new Learner("learner-1", Set.of(PREREQ)));
            learnerRepository.save(new Learner("learner-2", Set.of(PREREQ)));
            service.enroll(COURSE_ID, "learner-1");
            service.enroll(COURSE_ID, "learner-2");

            // Setup waitlist:
            // Pos 1: ineligible learner (missing CS-101)
            // Pos 2: eligible learner (has CS-101)
            learnerRepository.save(new Learner("ineligible-waitlist", Set.of("WRONG-COURSE")));
            learnerRepository.save(new Learner("eligible-waitlist", Set.of(PREREQ)));

            // Add directly to waitlist to test promotion scan over ineligible entry
            offering.enroll("temp-holder", Set.of(PREREQ)); // pos 1
            // Use package-private test helper for precise setup:
            offering.getWaitlist(); // unmodifiable check
            CourseOffering testOffering = new CourseOffering("SINGLE-CAP", "SC101", "Single Seat", 1, Set.of(PREREQ));
            offeringRepository.save(testOffering);
            service.enroll("SINGLE-CAP", "learner-1"); // fills capacity

            // Waitlist: pos 1 = ineligible, pos 2 = eligible
            testOffering.addToWaitlistForTesting("ineligible-waitlist", Set.of("WRONG-COURSE"));
            testOffering.addToWaitlistForTesting("eligible-waitlist", Set.of(PREREQ));

            assertThat(testOffering.getWaitlistCount()).isEqualTo(2);
            assertThat(testOffering.getWaitlistPosition("ineligible-waitlist")).hasValue(1);
            assertThat(testOffering.getWaitlistPosition("eligible-waitlist")).hasValue(2);

            // Action: drop learner-1
            com.example.courseregistration.domain.DropResult dropResult = service.drop("SINGLE-CAP", "learner-1");

            assertThat(dropResult.isSuccess()).isTrue();
            assertThat(dropResult.droppedLearnerId()).isEqualTo("learner-1");
            // Promotion skips ineligible-waitlist and promotes eligible-waitlist
            assertThat(dropResult.promotedLearnerId()).contains("eligible-waitlist");
            assertThat(dropResult.availableCapacity()).isEqualTo(0);

            // Assert: ineligible-waitlist is RETAINED at position 1 in the queue
            assertThat(testOffering.isEnrolled("eligible-waitlist")).isTrue();
            assertThat(testOffering.isEnrolled("ineligible-waitlist")).isFalse();
            assertThat(testOffering.isWaitlisted("ineligible-waitlist")).isTrue();
            assertThat(testOffering.getWaitlistPosition("ineligible-waitlist")).hasValue(1);
            assertThat(testOffering.getWaitlistCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Rule 10: When queue is entirely ineligible, leave seat vacant and retain all learners in queue")
        void shouldLeaveSeatVacantWhenQueueIsEntirelyIneligible() {
            CourseOffering testOffering = new CourseOffering("VACANT-TEST", "VT101", "Vacant Seat", 1, Set.of(PREREQ));
            offeringRepository.save(testOffering);
            learnerRepository.save(new Learner("learner-enrolled", Set.of(PREREQ)));
            service.enroll("VACANT-TEST", "learner-enrolled");

            // Waitlist contains two ineligible learners
            testOffering.addToWaitlistForTesting("ineligible-1", Set.of());
            testOffering.addToWaitlistForTesting("ineligible-2", Set.of("UNRELATED-101"));

            // Action: drop enrolled learner
            com.example.courseregistration.domain.DropResult dropResult = service.drop("VACANT-TEST", "learner-enrolled");

            assertThat(dropResult.isSuccess()).isTrue();
            assertThat(dropResult.promotedLearnerId()).isEmpty();
            assertThat(dropResult.availableCapacity()).isEqualTo(1); // seat remains vacant

            assertThat(testOffering.getEnrolledCount()).isEqualTo(0);
            assertThat(testOffering.getAvailableCapacity()).isEqualTo(1);
            assertThat(testOffering.getWaitlistCount()).isEqualTo(2);
            assertThat(testOffering.getWaitlistPosition("ineligible-1")).hasValue(1);
            assertThat(testOffering.getWaitlistPosition("ineligible-2")).hasValue(2);
        }

        @Test
        @DisplayName("Invalid and repeated drops: Dropping un-enrolled learner or repeated drops must not release seats or corrupt state")
        void shouldRejectInvalidAndRepeatedDrops() {
            learnerRepository.save(new Learner("learner-1", Set.of(PREREQ)));
            learnerRepository.save(new Learner("learner-unregistered", Set.of(PREREQ)));

            service.enroll(COURSE_ID, "learner-1");

            CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();
            assertThat(offering.getEnrolledCount()).isEqualTo(1);
            assertThat(offering.getAvailableCapacity()).isEqualTo(1);

            // 1. Drop learner who is not enrolled
            com.example.courseregistration.domain.DropResult invalidDrop = service.drop(COURSE_ID, "learner-unregistered");
            assertThat(invalidDrop.isSuccess()).isFalse();
            assertThat(invalidDrop.status()).isEqualTo(com.example.courseregistration.domain.DropStatus.NOT_ENROLLED);

            // State unchanged
            assertThat(offering.getEnrolledCount()).isEqualTo(1);
            assertThat(offering.getAvailableCapacity()).isEqualTo(1);

            // 2. Valid drop of learner-1
            com.example.courseregistration.domain.DropResult validDrop = service.drop(COURSE_ID, "learner-1");
            assertThat(validDrop.isSuccess()).isTrue();
            assertThat(offering.getEnrolledCount()).isEqualTo(0);
            assertThat(offering.getAvailableCapacity()).isEqualTo(2);

            // 3. Repeated drop of learner-1
            com.example.courseregistration.domain.DropResult repeatedDrop = service.drop(COURSE_ID, "learner-1");
            assertThat(repeatedDrop.isSuccess()).isFalse();
            assertThat(repeatedDrop.status()).isEqualTo(com.example.courseregistration.domain.DropStatus.NOT_ENROLLED);

            // State is not corrupted: capacity remains 2 (cannot release seat twice!)
            assertThat(offering.getEnrolledCount()).isEqualTo(0);
            assertThat(offering.getAvailableCapacity()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return failure results when offering or learner does not exist or input is blank")
        void shouldHandleDropValidationErrors() {
            learnerRepository.save(new Learner("learner-1", Set.of(PREREQ)));

            assertThat(service.drop("BAD-OFFERING", "learner-1").status())
                    .isEqualTo(com.example.courseregistration.domain.DropStatus.OFFERING_NOT_FOUND);

            assertThat(service.drop(COURSE_ID, "ghost-learner").status())
                    .isEqualTo(com.example.courseregistration.domain.DropStatus.LEARNER_NOT_FOUND);

            assertThat(service.drop("", "learner-1").status())
                    .isEqualTo(com.example.courseregistration.domain.DropStatus.INVALID_REQUEST);

            assertThat(service.drop(COURSE_ID, "  ").status())
                    .isEqualTo(com.example.courseregistration.domain.DropStatus.INVALID_REQUEST);
        }

        @Test
        @DisplayName("Should support dropOrThrow for consumers expecting domain exceptions")
        void shouldSupportDropOrThrow() {
            learnerRepository.save(new Learner("learner-1", Set.of(PREREQ)));
            service.enroll(COURSE_ID, "learner-1");

            // Successful dropOrThrow
            com.example.courseregistration.domain.DropResult result = service.dropOrThrow(COURSE_ID, "learner-1");
            assertThat(result.isSuccess()).isTrue();

            // Repeated drop throws NotEnrolledException
            assertThatThrownBy(() -> service.dropOrThrow(COURSE_ID, "learner-1"))
                    .isInstanceOf(com.example.courseregistration.domain.exception.NotEnrolledException.class);

            // Bad course throws CourseNotFoundException
            assertThatThrownBy(() -> service.dropOrThrow("UNKNOWN-COURSE", "learner-1"))
                    .isInstanceOf(CourseNotFoundException.class);

            // Bad learner throws LearnerNotFoundException
            assertThatThrownBy(() -> service.dropOrThrow(COURSE_ID, "unknown-learner"))
                    .isInstanceOf(LearnerNotFoundException.class);
        }

        @Test
        @DisplayName("Capacity and membership invariants: Step-by-step verification across full lifecycle")
        void shouldVerifyInvariantsAcrossFullLifecycle() {
            CourseOffering offering = offeringRepository.findById(COURSE_ID).orElseThrow();

            // Register 4 eligible learners
            for (int i = 1; i <= 4; i++) {
                learnerRepository.save(new Learner("student-" + i, Set.of(PREREQ)));
            }

            // Invariant initially: capacity = 2, enrolled = 0
            assertThat(offering.getEnrolledCount()).isEqualTo(0);
            assertThat(offering.getAvailableCapacity()).isEqualTo(2);

            // Enroll student-1
            service.enroll(COURSE_ID, "student-1");
            assertThat(offering.getEnrolledCount()).isEqualTo(1);
            assertThat(offering.getAvailableCapacity()).isEqualTo(1);
            assertThat(offering.getEnrolledLearners()).containsExactly("student-1");

            // Enroll student-2 -> reaches capacity 2/2
            service.enroll(COURSE_ID, "student-2");
            assertThat(offering.getEnrolledCount()).isEqualTo(2);
            assertThat(offering.getAvailableCapacity()).isEqualTo(0);
            assertThat(offering.isFull()).isTrue();

            // Enroll student-3 & student-4 -> join waitlist
            service.enroll(COURSE_ID, "student-3");
            service.enroll(COURSE_ID, "student-4");
            assertThat(offering.getWaitlistCount()).isEqualTo(2);

            // Invariant: Enrolled ∩ Waitlist == ∅
            assertThat(offering.getEnrolledLearners()).doesNotContain("student-3", "student-4");
            assertThat(offering.isWaitlisted("student-1")).isFalse();
            assertThat(offering.isWaitlisted("student-2")).isFalse();

            // Drop student-2 -> student-3 promoted
            service.drop(COURSE_ID, "student-2");
            assertThat(offering.isEnrolled("student-3")).isTrue();
            assertThat(offering.isEnrolled("student-2")).isFalse();
            assertThat(offering.getEnrolledCount()).isEqualTo(2);
            assertThat(offering.getWaitlistCount()).isEqualTo(1);
            assertThat(offering.getWaitlistPosition("student-4")).hasValue(1);

            // Drop student-1 -> student-4 promoted
            service.drop(COURSE_ID, "student-1");
            assertThat(offering.isEnrolled("student-4")).isTrue();
            assertThat(offering.getEnrolledCount()).isEqualTo(2);
            assertThat(offering.getWaitlistCount()).isEqualTo(0);

            // Drop student-3 -> no waitlist left, seat opens
            service.drop(COURSE_ID, "student-3");
            assertThat(offering.getEnrolledCount()).isEqualTo(1);
            assertThat(offering.getAvailableCapacity()).isEqualTo(1);

            // Drop student-4 -> both seats now open
            service.drop(COURSE_ID, "student-4");
            assertThat(offering.getEnrolledCount()).isEqualTo(0);
            assertThat(offering.getAvailableCapacity()).isEqualTo(2);
        }
    }
}
