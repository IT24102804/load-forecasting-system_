package com.example.loadforcasting.Entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(name = "user_email")
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false)
    private FeedbackType feedbackType;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FeedbackStatus status = FeedbackStatus.PENDING;

    @Column(name = "admin_reply", length = 1000)
    private String adminReply;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;



    // Constructors
    public Feedback() {}

    public Feedback(String userName, String userEmail, FeedbackType feedbackType,
                    String message, Integer rating) {
        this.userName = userName;
        this.userEmail = userEmail;
        this.feedbackType = feedbackType;
        this.message = message;
        this.rating = rating;
        this.status = FeedbackStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ========== GETTERS AND SETTERS ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public FeedbackType getFeedbackType() { return feedbackType; }
    public void setFeedbackType(FeedbackType feedbackType) { this.feedbackType = feedbackType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public FeedbackStatus getStatus() { return status; }
    public void setStatus(FeedbackStatus status) { this.status = status; }

    public String getAdminReply() { return adminReply; }
    public void setAdminReply(String adminReply) { this.adminReply = adminReply; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Column(name = "prediction_id")
    private Long predictionId;

    public Long getPredictionId() { return predictionId; }
    public void setPredictionId(Long predictionId) { this.predictionId = predictionId; }

    // Helper method for formatted date
    public String getFormattedDate() {
        if (createdAt == null) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a");
        return createdAt.format(formatter);
    }
}

