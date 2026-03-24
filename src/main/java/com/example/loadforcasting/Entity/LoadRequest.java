package com.example.loadforcasting.Entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "LoadRequests")
public class LoadRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "temperature", nullable = false)
    private double temperature;

    @Column(name = "humidity", nullable = false)
    private double humidity;

    @Column(name = "public_event", nullable = false)
    private int publicEvent;

    @Column(name = "predicted_load")
    private double predictedLoad;

    // ========== ANOMALY DETECTION FIELDS ==========
    @Column(name = "is_anomaly")
    private Boolean isAnomaly;

    @Column(name = "anomaly_score")
    private Double anomalyScore;

    @Column(name = "feedback_given")
    private Boolean feedbackGiven;

    @Column(name = "feedback_agreed")
    private Boolean feedbackAgreed;
    // ========== END ANOMALY FIELDS ==========

    public LoadRequest() {}

    // Existing Getters
    public Long getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public double getTemperature() { return temperature; }
    public double getHumidity() { return humidity; }
    public int getPublicEvent() { return publicEvent; }
    public double getPredictedLoad() { return predictedLoad; }

    // Existing Setters
    public void setId(Long id) { this.id = id; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public void setHumidity(double humidity) { this.humidity = humidity; }
    public void setPublicEvent(int publicEvent) { this.publicEvent = publicEvent; }
    public void setPredictedLoad(double predictedLoad) { this.predictedLoad = predictedLoad; }

    // ========== NEW GETTERS AND SETTERS ==========
    public Boolean getIsAnomaly() { return isAnomaly; }
    public void setIsAnomaly(Boolean isAnomaly) { this.isAnomaly = isAnomaly; }

    public Double getAnomalyScore() { return anomalyScore; }
    public void setAnomalyScore(Double anomalyScore) { this.anomalyScore = anomalyScore; }

    public Boolean getFeedbackGiven() { return feedbackGiven; }
    public void setFeedbackGiven(Boolean feedbackGiven) { this.feedbackGiven = feedbackGiven; }

    public Boolean getFeedbackAgreed() { return feedbackAgreed; }
    public void setFeedbackAgreed(Boolean feedbackAgreed) { this.feedbackAgreed = feedbackAgreed; }
    // ========== END NEW GETTERS/SETTERS ==========
}