package com.example.loadforcasting.Entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "anomalies")
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The prediction that triggered this anomaly
    @Column(name = "prediction_id")
    private Long predictionId;

    // Sensor readings at time of anomaly
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "load_demand", nullable = false)
    private double loadDemand;

    @Column(name = "temperature")
    private double temperature;

    @Column(name = "humidity")
    private double humidity;

    @Column(name = "hour_of_day")
    private int hourOfDay;

    @Column(name = "day_of_week")
    private int dayOfWeek;

    @Column(name = "month")
    private int month;

    // Detection output
    @Column(name = "anomaly_score")
    private double anomalyScore;

    @Column(name = "confidence")
    private Double confidence;

    // HIGH / MEDIUM / LOW
    @Column(name = "severity", nullable = false)
    private String severity;

    // Description of why it was flagged
    @Column(name = "reason", length = 500)
    private String reason;

    // OPEN / ACKNOWLEDGED / RESOLVED
    @Column(name = "status", nullable = false)
    private String status = "OPEN";

    // Admin notes when resolving
    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime detectedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // Was this a public event day?
    @Column(name = "public_event")
    private int publicEvent;

    @PrePersist
    protected void onCreate() {
        detectedAt = LocalDateTime.now();
        if (status == null) status = "OPEN";
    }

    // ===== Getters and Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPredictionId() { return predictionId; }
    public void setPredictionId(Long predictionId) { this.predictionId = predictionId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public double getLoadDemand() { return loadDemand; }
    public void setLoadDemand(double loadDemand) { this.loadDemand = loadDemand; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public double getHumidity() { return humidity; }
    public void setHumidity(double humidity) { this.humidity = humidity; }

    public int getHourOfDay() { return hourOfDay; }
    public void setHourOfDay(int hourOfDay) { this.hourOfDay = hourOfDay; }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public double getAnomalyScore() { return anomalyScore; }
    public void setAnomalyScore(double anomalyScore) { this.anomalyScore = anomalyScore; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public int getPublicEvent() { return publicEvent; }
    public void setPublicEvent(int publicEvent) { this.publicEvent = publicEvent; }

    // Helper: formatted detected date for display
    public String getFormattedDate() {
        if (detectedAt == null) return "";
        return detectedAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));
    }

    // Helper: severity badge color class
    public String getSeverityBadgeClass() {
        if (severity == null) return "bg-secondary";
        return switch (severity.toUpperCase()) {
            case "HIGH"   -> "badge-high";
            case "MEDIUM" -> "badge-medium";
            case "LOW"    -> "badge-low";
            default       -> "bg-secondary";
        };
    }

    // Helper: status badge color class
    public String getStatusBadgeClass() {
        if (status == null) return "bg-secondary";
        return switch (status.toUpperCase()) {
            case "OPEN"         -> "bg-danger";
            case "ACKNOWLEDGED" -> "bg-warning text-dark";
            case "RESOLVED"     -> "bg-success";
            default             -> "bg-secondary";
        };
    }
}
