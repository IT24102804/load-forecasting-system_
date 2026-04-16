package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Repository.FeedbackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FeedbackService {

    @Autowired
    private FeedbackRepository feedbackRepository;

    // ========== EXISTING METHODS ==========

    @Transactional
    public Feedback saveFeedback(Feedback feedback) {
        if (feedback.getUserName() == null || feedback.getUserName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (feedback.getMessage() == null || feedback.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Feedback message is required");
        }
        return feedbackRepository.save(feedback);
    }

    public List<Feedback> getAllFeedback() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<Feedback> getFeedbackById(Long id) {
        return feedbackRepository.findById(id);
    }

    public List<Feedback> getFeedbackByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return List.of();
        }
        return feedbackRepository.findByUserEmailOrderByCreatedAtDesc(email);
    }

    // ========== NEW EDIT METHODS ==========

    /**
     * Check if feedback can be edited (no admin reply)
     */
    public boolean isEditable(Long id) {
        Optional<Feedback> feedback = feedbackRepository.findById(id);
        if (feedback.isPresent()) {
            Feedback fb = feedback.get();
            // Can edit only if admin hasn't replied
            return fb.getAdminReply() == null || fb.getAdminReply().trim().isEmpty();
        }
        return false;
    }

    /**
     * UPDATE existing feedback
     */
    @Transactional
    public Feedback updateFeedback(Feedback updatedFeedback) {
        Feedback existing = feedbackRepository.findById(updatedFeedback.getId())
                .orElseThrow(() -> new RuntimeException("Feedback not found"));

        // Check if still editable
        if (!isEditable(updatedFeedback.getId())) {
            throw new RuntimeException("Cannot edit - admin has already replied");
        }

        // Update only allowed fields
        existing.setUserName(updatedFeedback.getUserName());
        existing.setFeedbackType(updatedFeedback.getFeedbackType());
        existing.setMessage(updatedFeedback.getMessage());
        existing.setRating(updatedFeedback.getRating());
        // Don't update email, status, admin_reply, createdAt

        return feedbackRepository.save(existing);
    }

    // ========== DELETE METHODS ==========

    public boolean isDeletable(Long id) {
        Optional<Feedback> feedback = feedbackRepository.findById(id);
        if (feedback.isPresent()) {
            Feedback fb = feedback.get();
            return fb.getAdminReply() == null || fb.getAdminReply().trim().isEmpty();
        }
        return false;
    }

    @Transactional
    public void deleteFeedback(Long id) {
        if (!feedbackRepository.existsById(id)) {
            throw new RuntimeException("Feedback not found with id: " + id);
        }
        feedbackRepository.deleteById(id);
    }

    @Transactional
    public void deleteByPredictionId(Long predictionId) {
        if (predictionId != null) {
            feedbackRepository.deleteByPredictionId(predictionId);
        }
    }
}
