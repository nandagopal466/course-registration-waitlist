package com.example.courseregistration.domain.exception;

public class LearnerNotFoundException extends CourseRegistrationDomainException {
    private final String learnerId;

    public LearnerNotFoundException(String learnerId) {
        super("Learner not found: " + learnerId);
        this.learnerId = learnerId;
    }

    public String getLearnerId() {
        return learnerId;
    }
}
