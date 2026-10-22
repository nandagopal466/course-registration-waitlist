package com.example.courseregistration.controller.dto;

import com.example.courseregistration.domain.EnrollmentResult;
import com.example.courseregistration.domain.EnrollmentStatus;

public record EnrollmentResponse(
        String offeringId,
        String learnerId,
        EnrollmentStatus status,
        Integer waitlistPosition,
        String message
) {
    public static EnrollmentResponse from(String offeringId, EnrollmentResult result) {
        return new EnrollmentResponse(
                offeringId,
                result.learnerId(),
                result.status(),
                result.waitlistPosition(),
                result.message()
        );
    }
}
