package com.example.courseregistration.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Domain record representing a learner and their completed course history.
 */
public record Learner(
        String learnerId,
        String name,
        Set<String> completedCourses
) {
    public Learner {
        Objects.requireNonNull(learnerId, "learnerId must not be null");
        if (learnerId.isBlank()) {
            throw new IllegalArgumentException("learnerId must not be blank");
        }
        name = (name == null || name.isBlank()) ? learnerId : name.trim();
        completedCourses = completedCourses == null ? Set.of() : Set.copyOf(completedCourses);
    }

    public Learner(String learnerId, Set<String> completedCourses) {
        this(learnerId, learnerId, completedCourses);
    }
}
