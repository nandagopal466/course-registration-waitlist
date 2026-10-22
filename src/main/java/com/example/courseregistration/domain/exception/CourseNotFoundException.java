package com.example.courseregistration.domain.exception;

public class CourseNotFoundException extends CourseRegistrationDomainException {
    private final String courseId;

    public CourseNotFoundException(String courseId) {
        super("Course offering not found: " + courseId);
        this.courseId = courseId;
    }

    public String getCourseId() {
        return courseId;
    }
}
