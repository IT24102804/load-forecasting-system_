package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Repository.FeedbackRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    // Find feedback by user email (for history view)
    List<Feedback> findByUserEmailOrderByCreatedAtDesc(String email);

    // Find feedback by user name (if no email)
    List<Feedback> findByUserNameContainingIgnoreCaseOrderByCreatedAtDesc(String name);

    // Get all feedback sorted by date (newest first)
    List<Feedback> findAllByOrderByCreatedAtDesc();

    void deleteByPredictionId(Long predictionId);
}
