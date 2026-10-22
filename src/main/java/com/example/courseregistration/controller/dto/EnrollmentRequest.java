package com.example.courseregistration.controller.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record EnrollmentRequest(
        @NotBlank(message = "learnerId is required and must not be blank")
        String learnerId,
        Set<String> completedCourses
) {
    public EnrollmentRequest(String learnerId) {
        this(learnerId, Set.of());
    }
}
