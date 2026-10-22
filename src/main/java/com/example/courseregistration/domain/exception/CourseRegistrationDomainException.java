package com.example.courseregistration.domain.exception;

public abstract class CourseRegistrationDomainException extends RuntimeException {
    protected CourseRegistrationDomainException(String message) {
        super(message);
    }
}
