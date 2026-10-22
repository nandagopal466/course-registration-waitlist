package com.example.courseregistration.domain;

/**
 * Domain status representing the outcome of an enrollment request.
 */
public enum EnrollmentStatus {
    ENROLLED,
    WAITLISTED,
    PREREQUISITE_NOT_MET,
    ALREADY_ENROLLED,
    ALREADY_WAITLISTED,
    OFFERING_NOT_FOUND,
    LEARNER_NOT_FOUND,
    INVALID_REQUEST
}
