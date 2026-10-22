package com.example.courseregistration.domain.exception;

public class NotEnrolledException extends CourseRegistrationDomainException {
    private final String learnerId;

    public NotEnrolledException(String learnerId) {
        super("Learner '%s' is not currently enrolled in this course offering.".formatted(learnerId));
        this.learnerId = learnerId;
    }

    public String getLearnerId() {
        return learnerId;
    }
}
