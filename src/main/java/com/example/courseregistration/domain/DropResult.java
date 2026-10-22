package com.example.courseregistration.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Domain result representing the outcome of a drop and waitlist promotion operation.
 */
public record DropResult(
        String droppedLearnerId,
        DropStatus status,
        Optional<String> promotedLearnerId,
        int availableCapacity,
        String message
) {
    public DropResult {
        Objects.requireNonNull(droppedLearnerId, "droppedLearnerId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(promotedLearnerId, "promotedLearnerId must not be null");
        if (availableCapacity < 0) {
            throw new IllegalArgumentException("availableCapacity cannot be negative");
        }
    }

    public DropResult(String droppedLearnerId, Optional<String> promotedLearnerId, int availableCapacity) {
        this(droppedLearnerId, DropStatus.DROPPED, promotedLearnerId, availableCapacity,
                promotedLearnerId.map(id -> "Learner dropped. Promoted learner " + id + " from waitlist.")
                        .orElse("Learner dropped. Seat remains vacant."));
    }

    public boolean isSuccess() {
        return status == DropStatus.DROPPED;
    }

    public boolean hasPromotion() {
        return promotedLearnerId.isPresent();
    }

    public boolean isFailure() {
        return !isSuccess();
    }

    public static DropResult dropped(String droppedLearnerId, Optional<String> promotedLearnerId, int availableCapacity, String message) {
        return new DropResult(droppedLearnerId, DropStatus.DROPPED, promotedLearnerId, availableCapacity, message);
    }

    public static DropResult failure(String droppedLearnerId, DropStatus status, String message) {
        if (status == DropStatus.DROPPED) {
            throw new IllegalArgumentException("Cannot create failure DropResult with status DROPPED");
        }
        return new DropResult(droppedLearnerId, status, Optional.empty(), 0, message);
    }
}
