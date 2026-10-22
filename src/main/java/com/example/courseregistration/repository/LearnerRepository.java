package com.example.courseregistration.repository;

import com.example.courseregistration.domain.Learner;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository for storing learner profiles and completed course records.
 */
@Repository
public class LearnerRepository {

    private final ConcurrentHashMap<String, Learner> storage = new ConcurrentHashMap<>();

    public Learner save(Learner learner) {
        Objects.requireNonNull(learner, "Learner must not be null");
        storage.put(learner.learnerId(), learner);
        return learner;
    }

    public Optional<Learner> findById(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(learnerId.trim()));
    }

    public boolean existsById(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            return false;
        }
        return storage.containsKey(learnerId.trim());
    }

    public List<Learner> findAll() {
        return new ArrayList<>(storage.values());
    }

    public long count() {
        return storage.size();
    }

    public void deleteById(String learnerId) {
        if (learnerId != null) {
            storage.remove(learnerId.trim());
        }
    }

    public void clear() {
        storage.clear();
    }
}
