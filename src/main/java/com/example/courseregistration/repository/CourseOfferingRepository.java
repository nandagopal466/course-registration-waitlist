package com.example.courseregistration.repository;

import com.example.courseregistration.domain.CourseOffering;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory storage for course offerings.
 * Uses ConcurrentHashMap to manage course offerings within a single JVM process.
 * Note: Data is volatile and does not persist across application restarts.
 */
@Repository
public class CourseOfferingRepository {

    private final ConcurrentHashMap<String, CourseOffering> storage = new ConcurrentHashMap<>();

    public CourseOffering save(CourseOffering offering) {
        Objects.requireNonNull(offering, "CourseOffering must not be null");
        storage.put(offering.getCourseId(), offering);
        return offering;
    }

    public Optional<CourseOffering> findById(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(courseId.trim()));
    }

    public List<CourseOffering> findAll() {
        return new ArrayList<>(storage.values());
    }

    public boolean existsById(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            return false;
        }
        return storage.containsKey(courseId.trim());
    }

    public long count() {
        return storage.size();
    }

    public void deleteById(String courseId) {
        if (courseId != null) {
            storage.remove(courseId.trim());
        }
    }

    public void clear() {
        storage.clear();
    }
}
