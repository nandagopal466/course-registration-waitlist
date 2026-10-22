package com.example.courseregistration.domain;

import java.util.Objects;

/**
 * Domain result representing the outcome of an enrollment request.
 */
public record EnrollmentResult(
        String learnerId,
        EnrollmentStatus status,
        Integer waitlistPosition,
        String message
) {
    public EnrollmentResult {
        Objects.requireNonNull(learnerId, "learnerId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (status == EnrollmentStatus.WAITLISTED && waitlistPosition == null) {
            throw new IllegalArgumentException("waitlistPosition must be provided when status is WAITLISTED");
        }
    }

    public EnrollmentResult(String learnerId, EnrollmentStatus status, Integer waitlistPosition) {
        this(learnerId, status, waitlistPosition, status.name());
    }

    public boolean isSuccess() {
        return status == EnrollmentStatus.ENROLLED || status == EnrollmentStatus.WAITLISTED;
    }

    public boolean isEnrolled() {
        return status == EnrollmentStatus.ENROLLED;
    }

    public boolean isWaitlisted() {
        return status == EnrollmentStatus.WAITLISTED;
    }

    public boolean isFailure() {
        return !isSuccess();
    }

    public static EnrollmentResult enrolled(String learnerId) {
        return new EnrollmentResult(learnerId, EnrollmentStatus.ENROLLED, null, "Learner successfully enrolled.");
    }

    public static EnrollmentResult enrolled(String learnerId, String message) {
        return new EnrollmentResult(learnerId, EnrollmentStatus.ENROLLED, null, message);
    }

    public static EnrollmentResult waitlisted(String learnerId, int waitlistPosition) {
        return new EnrollmentResult(learnerId, EnrollmentStatus.WAITLISTED, waitlistPosition,
                "Offering is full; learner placed on waitlist at position " + waitlistPosition + ".");
    }

    public static EnrollmentResult waitlisted(String learnerId, int waitlistPosition, String message) {
        return new EnrollmentResult(learnerId, EnrollmentStatus.WAITLISTED, waitlistPosition, message);
    }

    public static EnrollmentResult failure(String learnerId, EnrollmentStatus status, String message) {
        if (status == EnrollmentStatus.ENROLLED || status == EnrollmentStatus.WAITLISTED) {
            throw new IllegalArgumentException("Cannot create failure result with success status: " + status);
        }
        return new EnrollmentResult(learnerId, status, null, message);
    }
}
