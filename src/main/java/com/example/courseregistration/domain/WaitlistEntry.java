package com.example.courseregistration.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record WaitlistEntry(
        String learnerId,
        Set<String> completedCourses,
        Instant joinedAt
) {
    public WaitlistEntry {
        Objects.requireNonNull(learnerId, "learnerId must not be null");
        if (learnerId.isBlank()) {
            throw new IllegalArgumentException("learnerId must not be blank");
        }
        completedCourses = completedCourses == null ? Set.of() : Set.copyOf(completedCourses);
        joinedAt = joinedAt == null ? Instant.now() : joinedAt;
    }

    public WaitlistEntry(String learnerId, Set<String> completedCourses) {
        this(learnerId, completedCourses, Instant.now());
    }
}
