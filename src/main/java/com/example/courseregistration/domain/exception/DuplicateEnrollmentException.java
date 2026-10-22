package com.example.courseregistration.domain.exception;

public class DuplicateEnrollmentException extends CourseRegistrationDomainException {
    private final String learnerId;
    private final boolean alreadyWaitlisted;

    public DuplicateEnrollmentException(String learnerId, boolean alreadyWaitlisted) {
        super(alreadyWaitlisted
                ? "Learner '%s' is already waitlisted for this course offering.".formatted(learnerId)
                : "Learner '%s' is already enrolled in this course offering.".formatted(learnerId));
        this.learnerId = learnerId;
        this.alreadyWaitlisted = alreadyWaitlisted;
    }

    public String getLearnerId() {
        return learnerId;
    }

    public boolean isAlreadyWaitlisted() {
        return alreadyWaitlisted;
    }
}
