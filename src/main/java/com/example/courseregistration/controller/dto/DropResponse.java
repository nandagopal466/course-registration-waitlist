package com.example.courseregistration.controller.dto;

import com.example.courseregistration.domain.DropResult;
import com.example.courseregistration.domain.DropStatus;

public record DropResponse(
        String offeringId,
        String droppedLearnerId,
        DropStatus status,
        String promotedLearnerId,
        int availableCapacity,
        String message
) {
    public static DropResponse from(String offeringId, DropResult result) {
        return new DropResponse(
                offeringId,
                result.droppedLearnerId(),
                result.status(),
                result.promotedLearnerId().orElse(null),
                result.availableCapacity(),
                result.message()
        );
    }
}
