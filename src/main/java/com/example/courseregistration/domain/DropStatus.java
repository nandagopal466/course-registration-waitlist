package com.example.courseregistration.domain;

/**
 * Domain status representing the outcome of a drop request.
 */
public enum DropStatus {
    DROPPED,
    NOT_ENROLLED,
    OFFERING_NOT_FOUND,
    LEARNER_NOT_FOUND,
    INVALID_REQUEST
}
