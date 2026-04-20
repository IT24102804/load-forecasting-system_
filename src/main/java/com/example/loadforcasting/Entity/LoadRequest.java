package com.example.loadforcasting.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "LoadRequests")
public class LoadRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
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

    @Column(name = "input_signature", length = 200)
    private String inputSignature;

    @Column(name = "load_forecast_id")
    private Long loadForecastId;

    @Column(name = "load_forecast_run_id")
    private Long loadForecastRunId;

    @Column(name = "prediction_source", length = 50)
    private String source;

    @Column(name = "model_version_label", length = 100)
    private String modelVersionLabel;

    @Column(name = "estimated_daily_demand_mwh")
    private Double estimatedDailyDemandMwh;

    @Column(name = "run_reused")
    private Boolean runReused;

    @Transient
    private Boolean forceNew;

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
    public String getInputSignature() { return inputSignature; }
    public Long getLoadForecastId() { return loadForecastId; }
    public Long getLoadForecastRunId() { return loadForecastRunId; }
    public String getSource() { return source; }
    public String getModelVersionLabel() { return modelVersionLabel; }
    public Double getEstimatedDailyDemandMwh() { return estimatedDailyDemandMwh; }
    public Boolean getRunReused() { return runReused; }
    public Boolean getForceNew() { return forceNew; }

    // Existing Setters
    public void setId(Long id) { this.id = id; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public void setHumidity(double humidity) { this.humidity = humidity; }
    public void setPublicEvent(int publicEvent) { this.publicEvent = publicEvent; }
    public void setPredictedLoad(double predictedLoad) { this.predictedLoad = predictedLoad; }
    public void setInputSignature(String inputSignature) { this.inputSignature = inputSignature; }
    public void setLoadForecastId(Long loadForecastId) { this.loadForecastId = loadForecastId; }
    public void setLoadForecastRunId(Long loadForecastRunId) { this.loadForecastRunId = loadForecastRunId; }
    public void setSource(String source) { this.source = source; }
    public void setModelVersionLabel(String modelVersionLabel) { this.modelVersionLabel = modelVersionLabel; }
    public void setEstimatedDailyDemandMwh(Double estimatedDailyDemandMwh) { this.estimatedDailyDemandMwh = estimatedDailyDemandMwh; }
    public void setRunReused(Boolean runReused) { this.runReused = runReused; }
    public void setForceNew(Boolean forceNew) { this.forceNew = forceNew; }

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
