package com.example.courseregistration.domain;

import com.example.courseregistration.domain.exception.DuplicateEnrollmentException;
import com.example.courseregistration.domain.exception.InvalidCapacityException;
import com.example.courseregistration.domain.exception.NotEnrolledException;
import com.example.courseregistration.domain.exception.PrerequisiteNotMetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CourseOffering Domain Invariant Tests")
class CourseOfferingTest {

    @Nested
    @DisplayName("Creation & Validation")
    class CreationAndValidation {

        @Test
        @DisplayName("Should prevent non-positive capacity values")
        void shouldRejectInvalidCapacity() {
            assertThatThrownBy(() -> new CourseOffering("CS-101", "CS101", "Intro CS", 0, Set.of()))
                    .isInstanceOf(InvalidCapacityException.class)
                    .hasMessageContaining("strictly greater than zero");

            assertThatThrownBy(() -> new CourseOffering("CS-101", "CS101", "Intro CS", -5, Set.of()))
                    .isInstanceOf(InvalidCapacityException.class);
        }

        @Test
        @DisplayName("Should prevent blank identifiers or title")
        void shouldPreventBlankFields() {
            assertThatThrownBy(() -> new CourseOffering(" ", "CS101", "Intro CS", 5, Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> new CourseOffering("CS-101", " ", "Intro CS", 5, Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> new CourseOffering("CS-101", "CS101", "  ", 5, Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should initialize cleanly with derived state matching invariants")
        void shouldInitializeCleanly() {
            CourseOffering offering = new CourseOffering("CS-101", "CS101", "Intro to CS", 2, Set.of("MATH-100"));

            assertThat(offering.getCapacity()).isEqualTo(2);
            assertThat(offering.getEnrolledCount()).isEqualTo(0);
            assertThat(offering.getAvailableCapacity()).isEqualTo(2);
            assertThat(offering.isFull()).isFalse();
            assertThat(offering.getWaitlistCount()).isEqualTo(0);
            assertThat(offering.getPrerequisites()).containsExactly("MATH-100");
        }
    }

    @Nested
    @DisplayName("Enrollment & Invariant Rules")
    class EnrollmentRules {

        @Test
        @DisplayName("Rule 1: Eligible learner enrolls immediately if seat is available")
        void shouldEnrollEligibleLearnerWhenSeatsAvailable() {
            CourseOffering offering = new CourseOffering("CS-201", "CS201", "Algorithms", 2, Set.of("CS-101"));

            EnrollmentResult result = offering.enroll("learner-1", Set.of("CS-101"));

            assertThat(result.status()).isEqualTo(EnrollmentStatus.ENROLLED);
            assertThat(result.waitlistPosition()).isNull();
            assertThat(offering.getEnrolledCount()).isEqualTo(1);
            assertThat(offering.getAvailableCapacity()).isEqualTo(1);
            assertThat(offering.isEnrolled("learner-1")).isTrue();
            assertThat(offering.isWaitlisted("learner-1")).isFalse();
        }

        @Test
        @DisplayName("Rule 7: Ineligible learner cannot enroll directly")
        void shouldRejectIneligibleLearnerWhenPrerequisitesMissing() {
            CourseOffering offering = new CourseOffering("CS-201", "CS201", "Algorithms", 2, Set.of("CS-101"));

            assertThatThrownBy(() -> offering.enroll("learner-1", Set.of("CS-099")))
                    .isInstanceOf(PrerequisiteNotMetException.class)
                    .hasMessageContaining("does not satisfy required prerequisites");

            // State unchanged
            assertThat(offering.getEnrolledCount()).isEqualTo(0);
            assertThat(offering.getAvailableCapacity()).isEqualTo(2);
            assertThat(offering.isEnrolled("learner-1")).isFalse();
        }

        @Test
        @DisplayName("Rule 2: When full, eligible learner joins FIFO waitlist")
        void shouldWaitlistEligibleLearnerWhenCapacityFull() {
            CourseOffering offering = new CourseOffering("CS-201", "CS201", "Algorithms", 1, Set.of("CS-101"));

            // Fill course
            offering.enroll("learner-1", Set.of("CS-101"));
            assertThat(offering.isFull()).isTrue();

            // Next learner waitlists
            EnrollmentResult waitlistResult1 = offering.enroll("learner-2", Set.of("CS-101"));
            assertThat(waitlistResult1.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
            assertThat(waitlistResult1.waitlistPosition()).isEqualTo(1);

            // Third learner waitlists at position 2
            EnrollmentResult waitlistResult2 = offering.enroll("learner-3", Set.of("CS-101"));
            assertThat(waitlistResult2.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
            assertThat(waitlistResult2.waitlistPosition()).isEqualTo(2);

            assertThat(offering.getEnrolledCount()).isEqualTo(1);
            assertThat(offering.getWaitlistCount()).isEqualTo(2);
            assertThat(offering.isWaitlisted("learner-2")).isTrue();
            assertThat(offering.isWaitlisted("learner-3")).isTrue();
            assertThat(offering.getWaitlistPosition("learner-2")).hasValue(1);
            assertThat(offering.getWaitlistPosition("learner-3")).hasValue(2);
        }

        @Test
        @DisplayName("Rule 3 & 4: Duplicate enrollment requests must not create duplicate records")
        void shouldRejectDuplicateEnrollmentAndWaitlistEntries() {
            CourseOffering offering = new CourseOffering("CS-201", "CS201", "Algorithms", 1, Set.of());

            offering.enroll("learner-1", Set.of());

            // Duplicate enrollment attempt
            assertThatThrownBy(() -> offering.enroll("learner-1", Set.of()))
                    .isInstanceOf(DuplicateEnrollmentException.class)
                    .hasMessageContaining("already enrolled");

            // Waitlist learner-2
            offering.enroll("learner-2", Set.of());

            // Duplicate waitlist attempt
            assertThatThrownBy(() -> offering.enroll("learner-2", Set.of()))
                    .isInstanceOf(DuplicateEnrollmentException.class)
                    .hasMessageContaining("already waitlisted");

            // State uncorrupted
            assertThat(offering.getEnrolledCount()).isEqualTo(1);
            assertThat(offering.getWaitlistCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Drop & FIFO Promotion Policy")
    class DropAndPromotionRules {

        @Test
        @DisplayName("Rule 5 & 11: A learner can drop only when currently enrolled; repeated drops do not corrupt state")
        void shouldRejectDropWhenNotEnrolled() {
            CourseOffering offering = new CourseOffering("CS-201", "CS201", "Algorithms", 2, Set.of());

            // Unregistered learner
            assertThatThrownBy(() -> offering.drop("ghost-learner"))
                    .isInstanceOf(NotEnrolledException.class);

            offering.enroll("learner-1", Set.of());
            offering.drop("learner-1");

            // Repeated drop of same learner
            assertThatThrownBy(() -> offering.drop("learner-1"))
                    .isInstanceOf(NotEnrolledException.class);

            assertThat(offering.getEnrolledCount()).isEqualTo(0);
            assertThat(offering.getAvailableCapacity()).isEqualTo(2);
        }

        @Test
        @DisplayName("Rule 6: Dropping an enrolled learner frees a seat and promotes earliest eligible waitlisted learner")
        void shouldPromoteEarliestEligibleWaitlistedLearnerOnDrop() {
            CourseOffering offering = new CourseOffering("CS-201", "CS201", "Algorithms", 1, Set.of("CS-101"));

            offering.enroll("learner-1", Set.of("CS-101"));
            offering.enroll("learner-2", Set.of("CS-101")); // waitlisted at pos 1
            offering.enroll("learner-3", Set.of("CS-101")); // waitlisted at pos 2

            DropResult dropResult = offering.drop("learner-1");

            assertThat(dropResult.droppedLearnerId()).isEqualTo("learner-1");
            assertThat(dropResult.promotedLearnerId()).contains("learner-2");
            assertThat(dropResult.availableCapacity()).isEqualTo(0); // seat immediately filled by promotion

            assertThat(offering.isEnrolled("learner-1")).isFalse();
            assertThat(offering.isEnrolled("learner-2")).isTrue();
            assertThat(offering.isWaitlisted("learner-2")).isFalse();
            assertThat(offering.getWaitlistCount()).isEqualTo(1);
            assertThat(offering.getWaitlistPosition("learner-3")).hasValue(1);
        }

        @Test
        @DisplayName("Rule 8 & 9: Skip an ineligible waitlisted learner during promotion but retain them in the queue")
        void shouldSkipIneligibleWaitlistedLearnerAndRetainInQueue() {
            CourseOffering offering = new CourseOffering("CS-201", "CS201", "Algorithms", 1, Set.of("CS-101"));

            // Learner-1 enrolls directly
            offering.enroll("learner-1", Set.of("CS-101"));

            // Waitlist entry 1: learner-ineligible lacks CS-101
            offering.addToWaitlistForTesting("learner-ineligible", Set.of("CS-099"));

            // Waitlist entry 2: learner-eligible has CS-101
            offering.addToWaitlistForTesting("learner-eligible", Set.of("CS-101"));

            assertThat(offering.getWaitlistCount()).isEqualTo(2);
            assertThat(offering.getWaitlistPosition("learner-ineligible")).hasValue(1);
            assertThat(offering.getWaitlistPosition("learner-eligible")).hasValue(2);

            // Action: learner-1 drops
            DropResult dropResult = offering.drop("learner-1");

            // Ineligible learner-ineligible must be skipped, learner-eligible must be promoted
            assertThat(dropResult.droppedLearnerId()).isEqualTo("learner-1");
            assertThat(dropResult.promotedLearnerId()).contains("learner-eligible");
            assertThat(dropResult.availableCapacity()).isEqualTo(0);

            // Verify state invariants
            assertThat(offering.isEnrolled("learner-eligible")).isTrue();
            assertThat(offering.isEnrolled("learner-ineligible")).isFalse();
            assertThat(offering.isWaitlisted("learner-ineligible")).isTrue();
            // Crucial: learner-ineligible remains at position 1
            assertThat(offering.getWaitlistPosition("learner-ineligible")).hasValue(1);
            assertThat(offering.getWaitlistCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Rule 10: If all waitlisted learners are ineligible, leave seat vacant and retain all in queue")
        void shouldLeaveSeatVacantWhenAllWaitlistedLearnersAreIneligible() {
            CourseOffering offering = new CourseOffering("CS-201", "CS201", "Algorithms", 1, Set.of("CS-101"));

            offering.enroll("learner-1", Set.of("CS-101"));

            // Both waitlisted learners lack prerequisite CS-101
            offering.addToWaitlistForTesting("ineligible-1", Set.of("CS-099"));
            offering.addToWaitlistForTesting("ineligible-2", Set.of());

            DropResult dropResult = offering.drop("learner-1");

            // Seat remains vacant
            assertThat(dropResult.droppedLearnerId()).isEqualTo("learner-1");
            assertThat(dropResult.promotedLearnerId()).isEmpty();
            assertThat(dropResult.availableCapacity()).isEqualTo(1);

            assertThat(offering.getEnrolledCount()).isEqualTo(0);
            assertThat(offering.getWaitlistCount()).isEqualTo(2);
            assertThat(offering.getWaitlistPosition("ineligible-1")).hasValue(1);
            assertThat(offering.getWaitlistPosition("ineligible-2")).hasValue(2);
        }

        @Test
        @DisplayName("Rule 10: If waitlist is empty on drop, leave seat vacant")
        void shouldLeaveSeatVacantWhenWaitlistIsEmpty() {
            CourseOffering offering = new CourseOffering("CS-201", "CS201", "Algorithms", 2, Set.of());

            offering.enroll("learner-1", Set.of());
            assertThat(offering.getAvailableCapacity()).isEqualTo(1);

            DropResult dropResult = offering.drop("learner-1");

            assertThat(dropResult.droppedLearnerId()).isEqualTo("learner-1");
            assertThat(dropResult.promotedLearnerId()).isEmpty();
            assertThat(dropResult.availableCapacity()).isEqualTo(2);
            assertThat(offering.getEnrolledCount()).isEqualTo(0);
        }
    }
}
