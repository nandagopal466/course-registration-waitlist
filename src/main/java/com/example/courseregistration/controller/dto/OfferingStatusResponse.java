package com.example.courseregistration.controller.dto;

import com.example.courseregistration.domain.CourseOffering;
import com.example.courseregistration.domain.WaitlistEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record OfferingStatusResponse(
        String offeringId,
        String courseCode,
        String title,
        int capacity,
        int enrolledCount,
        int availableSeats,
        int waitlistCount,
        Set<String> prerequisites,
        Set<String> enrolledLearners,
        List<WaitlistEntryDto> waitlist
) {
    public record WaitlistEntryDto(
            int position,
            String learnerId,
            Instant joinedAt
    ) {}

    public static OfferingStatusResponse from(CourseOffering offering) {
        List<WaitlistEntryDto> waitlistDtos = new ArrayList<>();
        List<WaitlistEntry> waitlist = offering.getWaitlist();
        for (int i = 0; i < waitlist.size(); i++) {
            WaitlistEntry entry = waitlist.get(i);
            waitlistDtos.add(new WaitlistEntryDto(i + 1, entry.learnerId(), entry.joinedAt()));
        }

        return new OfferingStatusResponse(
                offering.getCourseId(),
                offering.getCourseCode(),
                offering.getTitle(),
                offering.getCapacity(),
                offering.getEnrolledCount(),
                offering.getAvailableCapacity(),
                offering.getWaitlistCount(),
                offering.getPrerequisites(),
                offering.getEnrolledLearners(),
                waitlistDtos
        );
    }
}
