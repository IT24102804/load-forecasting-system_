package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackReply;
import com.example.loadforcasting.Entity.FeedbackStatus;
import com.example.loadforcasting.Entity.LoadForecastRun;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Entity.User;
import com.example.loadforcasting.Repository.AnomalyRepository;
import com.example.loadforcasting.Repository.FeedbackReplyRepository;
import com.example.loadforcasting.Repository.FeedbackRepository;
import com.example.loadforcasting.Repository.LoadForecastRunRepository;
import com.example.loadforcasting.Repository.LoadRepository;
import com.example.loadforcasting.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FeedbackService {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackReplyRepository feedbackReplyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoadForecastRunRepository loadForecastRunRepository;

    @Autowired
    private LoadRepository loadRepository;

    @Autowired
    private AnomalyRepository anomalyRepository;

    @Transactional
    public Feedback saveFeedback(Feedback feedback) {
        return saveFeedback(feedback, null);
    }

    @Transactional
    public Feedback saveFeedback(Feedback feedback, Integer sessionUserId) {
        validateFeedback(feedback);
        resolveOwnership(feedback, sessionUserId);
        attachOperationalContext(feedback);
        inferSubjectScope(feedback);
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

    public List<Feedback> getFeedbackByUserId(Integer userId) {
        if (userId == null) {
            return List.of();
        }
        return feedbackRepository.findBySubmittedByUser_IdOrderByCreatedAtDesc(userId);
    }

    public boolean belongsToUser(Long feedbackId, Integer userId) {
        if (feedbackId == null || userId == null) {
            return false;
        }
        return feedbackRepository.findById(feedbackId)
                .map(Feedback::getSubmittedByUser)
                .map(User::getId)
                .map(id -> id == userId)
                .orElse(false);
    }

    public boolean isEditable(Long id) {
        Optional<Feedback> feedback = feedbackRepository.findById(id);
        if (feedback.isPresent()) {
            Feedback fb = feedback.get();
            return fb.getAdminReply() == null || fb.getAdminReply().trim().isEmpty();
        }
        return false;
    }

    @Transactional
    public Feedback updateFeedback(Feedback updatedFeedback) {
        return updateFeedback(updatedFeedback, null);
    }

    @Transactional
    public Feedback updateFeedback(Feedback updatedFeedback, Integer userId) {
        Feedback existing = feedbackRepository.findById(updatedFeedback.getId())
                .orElseThrow(() -> new RuntimeException("Feedback not found"));

        if (userId != null && !belongsToUser(updatedFeedback.getId(), userId)) {
            throw new RuntimeException("You can only edit your own feedback");
        }
        if (!isEditable(updatedFeedback.getId())) {
            throw new RuntimeException("Cannot edit - admin has already replied");
        }

        existing.setUserName(updatedFeedback.getUserName());
        existing.setFeedbackType(updatedFeedback.getFeedbackType());
        existing.setMessage(updatedFeedback.getMessage());
        existing.setRating(updatedFeedback.getRating());
        existing.setUserEmail(updatedFeedback.getUserEmail());
        existing.setContactEmailSnapshot(updatedFeedback.getUserEmail());
        if (userId != null) {
            userRepository.findById(userId).ifPresent(existing::setUpdatedByUser);
        }
        inferSubjectScope(existing);
        return feedbackRepository.save(existing);
    }

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
        deleteFeedback(id, null);
    }

    @Transactional
    public void deleteFeedback(Long id, Integer userId) {
        if (!feedbackRepository.existsById(id)) {
            throw new RuntimeException("Feedback not found with id: " + id);
        }
        if (userId != null && !belongsToUser(id, userId)) {
            throw new RuntimeException("You can only delete your own feedback");
        }
        feedbackRepository.deleteById(id);
    }

    @Transactional
    public Feedback replyFeedback(Long id, String replyMessage, Integer adminUserId) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feedback not found"));
        feedback.setAdminReply(replyMessage);
        feedback.setStatus(FeedbackStatus.RESOLVED);
        userRepository.findById(adminUserId).ifPresent(feedback::setUpdatedByUser);
        Feedback saved = feedbackRepository.save(feedback);

        if (adminUserId != null) {
            userRepository.findById(adminUserId).ifPresent(admin -> {
                FeedbackReply reply = new FeedbackReply();
                reply.setFeedback(saved);
                reply.setRepliedByUser(admin);
                reply.setReplyMessage(replyMessage);
                feedbackReplyRepository.save(reply);
            });
        }

        return saved;
    }

    @Transactional
    public void deleteByPredictionId(Long predictionId) {
        if (predictionId != null) {
            feedbackRepository.deleteByPredictionId(predictionId);
        }
    }

    private void validateFeedback(Feedback feedback) {
        if (feedback.getUserName() == null || feedback.getUserName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (feedback.getMessage() == null || feedback.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Feedback message is required");
        }
    }

    private void resolveOwnership(Feedback feedback, Integer sessionUserId) {
        if (sessionUserId != null) {
            userRepository.findById(sessionUserId).ifPresent(user -> {
                feedback.setSubmittedByUser(user);
                if (feedback.getUserName() == null || feedback.getUserName().isBlank()) {
                    feedback.setUserName(user.getName());
                }
                if (feedback.getUserEmail() == null || feedback.getUserEmail().isBlank()) {
                    feedback.setUserEmail(user.getEmail());
                }
                feedback.setContactEmailSnapshot(user.getEmail());
            });
        } else if (feedback.getUserEmail() != null) {
            feedback.setContactEmailSnapshot(feedback.getUserEmail());
            userRepository.findAll().stream()
                    .filter(user -> feedback.getUserEmail().equalsIgnoreCase(user.getEmail()))
                    .findFirst()
                    .ifPresent(feedback::setSubmittedByUser);
        }
    }

    private void attachOperationalContext(Feedback feedback) {
        if (feedback.getLoadForecastRun() == null && feedback.getPredictionId() != null) {
            loadRepository.findById(feedback.getPredictionId())
                    .map(LoadRequest::getLoadForecastRunId)
                    .flatMap(loadForecastRunRepository::findById)
                    .ifPresent(feedback::setLoadForecastRun);
        }

        if (feedback.getAnomaly() == null) {
            if (feedback.getLoadForecastRun() != null) {
                anomalyRepository.findFirstByLoadForecastRun_IdOrderByDetectedAtDesc(feedback.getLoadForecastRun().getId())
                        .ifPresent(feedback::setAnomaly);
            }

            if (feedback.getAnomaly() == null && feedback.getPredictionId() != null) {
                anomalyRepository.findFirstByPredictionIdOrderByDetectedAtDesc(feedback.getPredictionId())
                        .ifPresent(feedback::setAnomaly);
            }
        }

        if (feedback.getLoadForecastRun() == null && feedback.getAnomaly() != null && feedback.getAnomaly().getLoadForecastRun() != null) {
            feedback.setLoadForecastRun(feedback.getAnomaly().getLoadForecastRun());
        }
    }

    private void inferSubjectScope(Feedback feedback) {
        if (feedback.getAnomaly() != null) {
            feedback.setSubjectScope("ANOMALY");
        } else if (feedback.getLoadForecastRun() != null) {
            feedback.setSubjectScope("LOAD_FORECAST");
        } else {
            feedback.setSubjectScope("GENERAL");
        }
    }
}
