package com.example.loadforcasting.Entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "load_forecast_runs")
public class LoadForecastRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "load_forecast_id", nullable = false)
    private LoadForecast loadForecast;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_version_id", nullable = false)
    private ModelVersion modelVersion;

    @Column(name = "predicted_load_kw", nullable = false)
    private double predictedLoadKw;

    @Column(name = "estimated_daily_demand_mwh", nullable = false)
    private double estimatedDailyDemandMwh;

    @Column(name = "daily_demand_method", nullable = false, length = 100)
    private String dailyDemandMethod;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "run_reason", nullable = false, length = 100)
    private String runReason;

    @Column(name = "is_reused", nullable = false)
    private boolean reused;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LoadForecast getLoadForecast() {
        return loadForecast;
    }

    public void setLoadForecast(LoadForecast loadForecast) {
        this.loadForecast = loadForecast;
    }

    public ModelVersion getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(ModelVersion modelVersion) {
        this.modelVersion = modelVersion;
    }

    public double getPredictedLoadKw() {
        return predictedLoadKw;
    }

    public void setPredictedLoadKw(double predictedLoadKw) {
        this.predictedLoadKw = predictedLoadKw;
    }

    public double getEstimatedDailyDemandMwh() {
        return estimatedDailyDemandMwh;
    }

    public void setEstimatedDailyDemandMwh(double estimatedDailyDemandMwh) {
        this.estimatedDailyDemandMwh = estimatedDailyDemandMwh;
    }

    public String getDailyDemandMethod() {
        return dailyDemandMethod;
    }

    public void setDailyDemandMethod(String dailyDemandMethod) {
        this.dailyDemandMethod = dailyDemandMethod;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRunReason() {
        return runReason;
    }

    public void setRunReason(String runReason) {
        this.runReason = runReason;
    }

    public boolean isReused() {
        return reused;
    }

    public void setReused(boolean reused) {
        this.reused = reused;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
