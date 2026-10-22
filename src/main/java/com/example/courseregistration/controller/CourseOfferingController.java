package com.example.courseregistration.controller;

import com.example.courseregistration.controller.dto.DropResponse;
import com.example.courseregistration.controller.dto.EnrollmentRequest;
import com.example.courseregistration.controller.dto.EnrollmentResponse;
import com.example.courseregistration.controller.dto.OfferingStatusResponse;
import com.example.courseregistration.domain.CourseOffering;
import com.example.courseregistration.domain.DropResult;
import com.example.courseregistration.domain.EnrollmentResult;
import com.example.courseregistration.domain.exception.CourseNotFoundException;
import com.example.courseregistration.service.CourseRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * REST controller exposing endpoints for course offering enrollment, drops, and status inspections.
 * Delegates all business logic to CourseRegistrationService.
 */
@RestController
@RequestMapping("/api/offerings")
public class CourseOfferingController {

    private final CourseRegistrationService registrationService;

    public CourseOfferingController(CourseRegistrationService registrationService) {
        this.registrationService = Objects.requireNonNull(registrationService, "registrationService must not be null");
    }

    /**
     * 1. POST /api/offerings/{offeringId}/enrollments
     * Enrolls the learner or adds them to the FIFO waitlist.
     */
    @PostMapping("/{offeringId}/enrollments")
    public ResponseEntity<EnrollmentResponse> enroll(
            @PathVariable String offeringId,
            @Valid @RequestBody EnrollmentRequest request
    ) {
        EnrollmentResult result = registrationService.enrollOrThrow(
                offeringId,
                request.learnerId(),
                request.completedCourses()
        );
        return ResponseEntity.ok(EnrollmentResponse.from(offeringId, result));
    }

    /**
     * 2. DELETE /api/offerings/{offeringId}/enrollments/{learnerId}
     * Drops an enrolled learner and triggers FIFO waitlist promotion.
     */
    @DeleteMapping("/{offeringId}/enrollments/{learnerId}")
    public ResponseEntity<DropResponse> drop(
            @PathVariable String offeringId,
            @PathVariable String learnerId
    ) {
        DropResult result = registrationService.dropOrThrow(offeringId, learnerId);
        return ResponseEntity.ok(DropResponse.from(offeringId, result));
    }

    /**
     * 3. GET /api/offerings/{offeringId}
     * Retrieves course offering status, capacity, enrolled count, available seats, and waitlist.
     */
    @GetMapping("/{offeringId}")
    public ResponseEntity<OfferingStatusResponse> getOfferingStatus(@PathVariable String offeringId) {
        CourseOffering offering = registrationService.getOffering(offeringId)
                .orElseThrow(() -> new CourseNotFoundException(offeringId));

        return ResponseEntity.ok(OfferingStatusResponse.from(offering));
    }
}
