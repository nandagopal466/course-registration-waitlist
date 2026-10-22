package com.example.courseregistration.repository;

import com.example.courseregistration.domain.CourseOffering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CourseOfferingRepository In-Memory Storage Tests")
class CourseOfferingRepositoryTest {

    private CourseOfferingRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CourseOfferingRepository();
    }

    @Test
    @DisplayName("Should save and retrieve course offering by ID")
    void shouldSaveAndFindById() {
        CourseOffering offering = new CourseOffering("CS-101", "CS101", "Intro CS", 3, Set.of());
        repository.save(offering);

        Optional<CourseOffering> retrieved = repository.findById("CS-101");

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getCourseId()).isEqualTo("CS-101");
        assertThat(retrieved.get().getTitle()).isEqualTo("Intro CS");
    }

    @Test
    @DisplayName("Should return empty Optional for non-existent ID")
    void shouldReturnEmptyForNonExistentId() {
        assertThat(repository.findById("NON-EXISTENT")).isEmpty();
        assertThat(repository.findById("")).isEmpty();
        assertThat(repository.findById(null)).isEmpty();
    }

    @Test
    @DisplayName("Should list all offerings")
    void shouldFindAllOfferings() {
        repository.save(new CourseOffering("CS-101", "CS101", "Intro", 2, Set.of()));
        repository.save(new CourseOffering("CS-102", "CS102", "Data Structures", 2, Set.of()));

        List<CourseOffering> all = repository.findAll();

        assertThat(all).hasSize(2);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.existsById("CS-101")).isTrue();
        assertThat(repository.existsById("CS-102")).isTrue();
        assertThat(repository.existsById("CS-999")).isFalse();
    }

    @Test
    @DisplayName("Should delete offering by ID and clear entire storage")
    void shouldDeleteAndClear() {
        repository.save(new CourseOffering("CS-101", "CS101", "Intro", 2, Set.of()));
        repository.save(new CourseOffering("CS-102", "CS102", "Data Structures", 2, Set.of()));

        repository.deleteById("CS-101");
        assertThat(repository.existsById("CS-101")).isFalse();
        assertThat(repository.count()).isEqualTo(1);

        repository.clear();
        assertThat(repository.count()).isEqualTo(0);
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Should safely handle concurrent saves and queries")
    void shouldHandleConcurrentAccessSafely() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger successCounter = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    repository.save(new CourseOffering("CS-" + index, "CS" + index, "Course " + index, 5, Set.of()));
                    if (repository.findById("CS-" + index).isPresent()) {
                        successCounter.incrementAndGet();
                    }
                } catch (Exception e) {
                    // unexpected
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCounter.get()).isEqualTo(threadCount);
        assertThat(repository.count()).isEqualTo(threadCount);
    }
}
