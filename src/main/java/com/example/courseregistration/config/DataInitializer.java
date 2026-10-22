package com.example.courseregistration.config;

import com.example.courseregistration.domain.CourseOffering;
import com.example.courseregistration.domain.Learner;
import com.example.courseregistration.repository.CourseOfferingRepository;
import com.example.courseregistration.repository.LearnerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Initializes the in-memory store with a default course offering and sample learners on startup.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final CourseOfferingRepository courseOfferingRepository;
    private final LearnerRepository learnerRepository;

    public DataInitializer(CourseOfferingRepository courseOfferingRepository, LearnerRepository learnerRepository) {
        this.courseOfferingRepository = courseOfferingRepository;
        this.learnerRepository = learnerRepository;
    }

    @Override
    public void run(String... args) {
        if (courseOfferingRepository.count() == 0) {
            CourseOffering defaultOffering = new CourseOffering(
                    "CS-201",
                    "CS201",
                    "Data Structures and Algorithms",
                    3,
                    Set.of("CS-101")
            );
            courseOfferingRepository.save(defaultOffering);
        }

        if (learnerRepository.count() == 0) {
            // Seed a sample eligible learner
            learnerRepository.save(new Learner("learner-101", "Alice Student", Set.of("CS-101")));
            learnerRepository.save(new Learner("learner-102", "Bob Student", Set.of("CS-101")));
            learnerRepository.save(new Learner("learner-103", "Charlie Student", Set.of("CS-101")));
            learnerRepository.save(new Learner("learner-104", "Diana Student", Set.of("CS-101")));
            // Seed an ineligible learner
            learnerRepository.save(new Learner("learner-ineligible", "Eve Newbie", Set.of()));
        }
    }
}
