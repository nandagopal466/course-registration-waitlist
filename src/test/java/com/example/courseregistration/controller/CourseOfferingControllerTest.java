package com.example.courseregistration.controller;

import com.example.courseregistration.domain.CourseOffering;
import com.example.courseregistration.domain.Learner;
import com.example.courseregistration.repository.CourseOfferingRepository;
import com.example.courseregistration.repository.LearnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Set;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DisplayName("CourseOfferingController REST API Tests")
class CourseOfferingControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private CourseOfferingRepository offeringRepository;

    @Autowired
    private LearnerRepository learnerRepository;

    private static final String OFFERING_ID = "CS-API-101";
    private static final String PREREQ = "CS-100";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        offeringRepository.clear();
        learnerRepository.clear();

        // Seed offering with capacity = 2 and prerequisite CS-100
        CourseOffering offering = new CourseOffering(OFFERING_ID, "CS101", "REST Intro", 2, Set.of(PREREQ));
        offeringRepository.save(offering);

        // Seed eligible learners
        learnerRepository.save(new Learner("student-1", Set.of(PREREQ)));
        learnerRepository.save(new Learner("student-2", Set.of(PREREQ)));
        learnerRepository.save(new Learner("student-3", Set.of(PREREQ)));
        learnerRepository.save(new Learner("student-4", Set.of(PREREQ)));

        // Seed ineligible learner
        learnerRepository.save(new Learner("student-ineligible", Set.of("MATH-099")));
    }

    @Nested
    @DisplayName("POST /api/offerings/{offeringId}/enrollments")
    class EnrollmentEndpointTests {

        @Test
        @DisplayName("Should successfully enroll eligible learner when capacity is available")
        void shouldEnrollSuccessfully() throws Exception {
            String requestBody = """
                    {
                        "learnerId": "student-1"
                    }
                    """;

            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offeringId", equalTo(OFFERING_ID)))
                    .andExpect(jsonPath("$.learnerId", equalTo("student-1")))
                    .andExpect(jsonPath("$.status", equalTo("ENROLLED")))
                    .andExpect(jsonPath("$.waitlistPosition", nullValue()))
                    .andExpect(jsonPath("$.message", notNullValue()));
        }

        @Test
        @DisplayName("Should add eligible learner to waitlist when capacity is full")
        void shouldWaitlistWhenFull() throws Exception {
            // Fill capacity (2/2)
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"learnerId\": \"student-1\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"learnerId\": \"student-2\"}"))
                    .andExpect(status().isOk());

            // Third eligible learner should be WAITLISTED at position 1
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"learnerId\": \"student-3\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", equalTo("WAITLISTED")))
                    .andExpect(jsonPath("$.waitlistPosition", equalTo(1)))
                    .andExpect(jsonPath("$.message", notNullValue()));

            // Fourth eligible learner should be WAITLISTED at position 2
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"learnerId\": \"student-4\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", equalTo("WAITLISTED")))
                    .andExpect(jsonPath("$.waitlistPosition", equalTo(2)));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when learner lacks required prerequisite")
        void shouldRejectPrerequisiteFailure() throws Exception {
            String requestBody = """
                    {
                        "learnerId": "student-ineligible"
                    }
                    """;

            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", equalTo(400)))
                    .andExpect(jsonPath("$.code", equalTo("PREREQUISITE_NOT_MET")))
                    .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("MATH-099")));
        }

        @Test
        @DisplayName("Should return 404 Not Found when learner does not exist on enrollment")
        void shouldReturn404WhenLearnerDoesNotExistOnEnrollment() throws Exception {
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"learnerId\": \"ghost-learner\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", equalTo(404)))
                    .andExpect(jsonPath("$.code", equalTo("LEARNER_NOT_FOUND")));
        }

        @Test
        @DisplayName("Should return 409 Conflict when learner is already enrolled")
        void shouldRejectDuplicateEnrollment() throws Exception {
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"learnerId\": \"student-1\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"learnerId\": \"student-1\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status", equalTo(409)))
                    .andExpect(jsonPath("$.code", equalTo("ALREADY_ENROLLED")));
        }

        @Test
        @DisplayName("Should return 409 Conflict when learner is already waitlisted")
        void shouldRejectDuplicateWaitlist() throws Exception {
            // Fill offering
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\": \"student-1\"}"));
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\": \"student-2\"}"));

            // Waitlist student-3
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\": \"student-3\"}"));

            // Re-attempt for student-3
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"learnerId\": \"student-3\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status", equalTo(409)))
                    .andExpect(jsonPath("$.code", equalTo("ALREADY_WAITLISTED")));
        }

        @Test
        @DisplayName("Should return 404 Not Found when course offering does not exist")
        void shouldReturn404ForUnknownOffering() throws Exception {
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", "NON-EXISTENT")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"learnerId\": \"student-1\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", equalTo("OFFERING_NOT_FOUND")));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when learnerId is blank (Jakarta Validation)")
        void shouldReturn400ForBlankLearnerId() throws Exception {
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"learnerId\": \"   \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));
        }
    }

    @Nested
    @DisplayName("DELETE /api/offerings/{offeringId}/enrollments/{learnerId}")
    class DropEndpointTests {

        @Test
        @DisplayName("Should successfully drop enrolled learner and promote earliest eligible waitlisted learner")
        void shouldDropAndPromoteWaitlistedLearner() throws Exception {
            // Enroll student-1 and student-2 (full: 2/2)
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\": \"student-1\"}"));
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\": \"student-2\"}"));

            // Waitlist student-3
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\": \"student-3\"}"));

            // Action: Drop student-1 -> student-3 should be promoted
            mockMvc.perform(delete("/api/offerings/{offeringId}/enrollments/{learnerId}", OFFERING_ID, "student-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offeringId", equalTo(OFFERING_ID)))
                    .andExpect(jsonPath("$.droppedLearnerId", equalTo("student-1")))
                    .andExpect(jsonPath("$.status", equalTo("DROPPED")))
                    .andExpect(jsonPath("$.promotedLearnerId", equalTo("student-3")))
                    .andExpect(jsonPath("$.availableCapacity", equalTo(0)));
        }

        @Test
        @DisplayName("Should skip ineligible waitlisted learner and promote next eligible learner via REST")
        void shouldSkipIneligibleWaitlistedLearnerAndPromoteEligibleOverRest() throws Exception {
            // Fill capacity (2/2) with student-1 and student-2
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\": \"student-1\"}"));
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\": \"student-2\"}"));

            // Insert student-ineligible at waitlist position 1 (lacks CS-100)
            CourseOffering offering = offeringRepository.findById(OFFERING_ID).orElseThrow();
            offering.addToWaitlistForTesting("student-ineligible", Set.of("MATH-099"));

            // Waitlist student-3 at waitlist position 2 (has CS-100)
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\": \"student-3\"}"));

            // Action: Drop student-1 -> student-3 must be promoted, student-ineligible retained at pos 1
            mockMvc.perform(delete("/api/offerings/{offeringId}/enrollments/{learnerId}", OFFERING_ID, "student-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.droppedLearnerId", equalTo("student-1")))
                    .andExpect(jsonPath("$.promotedLearnerId", equalTo("student-3")))
                    .andExpect(jsonPath("$.availableCapacity", equalTo(0)));

            // Inspect status: student-ineligible remains on waitlist at position 1
            mockMvc.perform(get("/api/offerings/{offeringId}", OFFERING_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enrolledCount", equalTo(2)))
                    .andExpect(jsonPath("$.waitlistCount", equalTo(1)))
                    .andExpect(jsonPath("$.waitlist[0].learnerId", equalTo("student-ineligible")))
                    .andExpect(jsonPath("$.waitlist[0].position", equalTo(1)));
        }

        @Test
        @DisplayName("Should leave seat vacant when waitlist is empty on drop")
        void shouldDropWithoutPromotionWhenWaitlistEmpty() throws Exception {
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\": \"student-1\"}"));

            mockMvc.perform(delete("/api/offerings/{offeringId}/enrollments/{learnerId}", OFFERING_ID, "student-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.droppedLearnerId", equalTo("student-1")))
                    .andExpect(jsonPath("$.status", equalTo("DROPPED")))
                    .andExpect(jsonPath("$.promotedLearnerId", nullValue()))
                    .andExpect(jsonPath("$.availableCapacity", equalTo(2)));
        }

        @Test
        @DisplayName("Should return 404 Not Found when attempting to drop an unenrolled learner")
        void shouldRejectDropWhenNotEnrolled() throws Exception {
            mockMvc.perform(delete("/api/offerings/{offeringId}/enrollments/{learnerId}", OFFERING_ID, "student-1"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", equalTo(404)))
                    .andExpect(jsonPath("$.code", equalTo("NOT_ENROLLED")));
        }

        @Test
        @DisplayName("Should return 404 Not Found on repeated drop of already dropped learner")
        void shouldRejectRepeatedDrop() throws Exception {
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\": \"student-1\"}"));

            // First drop succeeds
            mockMvc.perform(delete("/api/offerings/{offeringId}/enrollments/{learnerId}", OFFERING_ID, "student-1"))
                    .andExpect(status().isOk());

            // Second drop returns 404 Not Found
            mockMvc.perform(delete("/api/offerings/{offeringId}/enrollments/{learnerId}", OFFERING_ID, "student-1"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", equalTo("NOT_ENROLLED")));
        }

        @Test
        @DisplayName("Should return 404 Not Found when learner does not exist on drop")
        void shouldReturn404WhenLearnerDoesNotExistOnDrop() throws Exception {
            mockMvc.perform(delete("/api/offerings/{offeringId}/enrollments/{learnerId}", OFFERING_ID, "ghost-learner"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", equalTo(404)))
                    .andExpect(jsonPath("$.code", equalTo("LEARNER_NOT_FOUND")));
        }

        @Test
        @DisplayName("Should return 404 Not Found when offering does not exist on drop")
        void shouldReturn404WhenOfferingDoesNotExistOnDrop() throws Exception {
            mockMvc.perform(delete("/api/offerings/{offeringId}/enrollments/{learnerId}", "NON-EXISTENT", "student-1"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", equalTo(404)))
                    .andExpect(jsonPath("$.code", equalTo("OFFERING_NOT_FOUND")));
        }
    }

    @Nested
    @DisplayName("GET /api/offerings/{offeringId}")
    class StatusEndpointTests {

        @Test
        @DisplayName("Should return offering status, capacity, enrolled learners and waitlist")
        void shouldReturnOfferingStatus() throws Exception {
            mockMvc.perform(post("/api/offerings/{offeringId}/enrollments", OFFERING_ID)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\": \"student-1\"}"));

            mockMvc.perform(get("/api/offerings/{offeringId}", OFFERING_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offeringId", equalTo(OFFERING_ID)))
                    .andExpect(jsonPath("$.courseCode", equalTo("CS101")))
                    .andExpect(jsonPath("$.title", equalTo("REST Intro")))
                    .andExpect(jsonPath("$.capacity", equalTo(2)))
                    .andExpect(jsonPath("$.enrolledCount", equalTo(1)))
                    .andExpect(jsonPath("$.availableSeats", equalTo(1)))
                    .andExpect(jsonPath("$.waitlistCount", equalTo(0)))
                    .andExpect(jsonPath("$.enrolledLearners", hasSize(1)))
                    .andExpect(jsonPath("$.waitlist", empty()));
        }

        @Test
        @DisplayName("Should return 404 Not Found for non-existent offering")
        void shouldReturn404ForUnknownOfferingStatus() throws Exception {
            mockMvc.perform(get("/api/offerings/{offeringId}", "NON-EXISTENT"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", equalTo("OFFERING_NOT_FOUND")));
        }
    }
}
