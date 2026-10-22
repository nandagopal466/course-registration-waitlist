package com.example.courseregistration.domain.exception;

import java.util.Set;

public class PrerequisiteNotMetException extends CourseRegistrationDomainException {
    private final String learnerId;
    private final Set<String> requiredPrerequisites;
    private final Set<String> completedCourses;

    public PrerequisiteNotMetException(String learnerId, Set<String> requiredPrerequisites, Set<String> completedCourses) {
        super("Learner '%s' does not satisfy required prerequisites: %s. Completed: %s"
                .formatted(learnerId, requiredPrerequisites, completedCourses));
        this.learnerId = learnerId;
        this.requiredPrerequisites = requiredPrerequisites;
        this.completedCourses = completedCourses;
    }

    public String getLearnerId() {
        return learnerId;
    }

    public Set<String> getRequiredPrerequisites() {
        return requiredPrerequisites;
    }

    public Set<String> getCompletedCourses() {
        return completedCourses;
    }
}
