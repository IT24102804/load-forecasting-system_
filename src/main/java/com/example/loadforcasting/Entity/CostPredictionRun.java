package com.example.loadforcasting.Entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "cost_prediction_runs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cost_prediction_combo",
                        columnNames = {"generation_mix_result_id", "fo_price", "coal_price", "diesel_price", "naphtha_price"}
                )
        }
)
public class CostPredictionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_mix_result_id")
    private GenerationMixRun generationMixRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_request_id")
    private CostPredictionRequestEntity costPredictionRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_version_id")
    private ModelVersion modelVersion;

    @Column(name = "forecast_timestamp")
    private LocalDateTime forecastTimestamp;

    @Column(name = "fo_price")
    private Double foPrice;

    @Column(name = "coal_price")
    private Double coalPrice;

    @Column(name = "diesel_price")
    private Double dieselPrice;

    @Column(name = "naphtha_price")
    private Double naphthaPrice;

    @Column(name = "unit_cost")
    private Double unitCost;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "run_reason", length = 100)
    private String runReason;

    @Column(name = "is_reused")
    private Boolean reused;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public CostPredictionRun() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGenerationMixResultId() {
        return generationMixRun != null ? generationMixRun.getId() : null;
    }

    public void setGenerationMixResultId(Long generationMixResultId) {
    }

    public GenerationMixRun getGenerationMixRun() {
        return generationMixRun;
    }

    public void setGenerationMixRun(GenerationMixRun generationMixRun) {
        this.generationMixRun = generationMixRun;
    }

    public CostPredictionRequestEntity getCostPredictionRequest() {
        return costPredictionRequest;
    }

    public void setCostPredictionRequest(CostPredictionRequestEntity costPredictionRequest) {
        this.costPredictionRequest = costPredictionRequest;
    }

    public ModelVersion getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(ModelVersion modelVersion) {
        this.modelVersion = modelVersion;
    }

    public LocalDateTime getForecastTimestamp() {
        return forecastTimestamp;
    }

    public void setForecastTimestamp(LocalDateTime forecastTimestamp) {
        this.forecastTimestamp = forecastTimestamp;
    }

    public Double getFoPrice() {
        return foPrice;
    }

    public void setFoPrice(Double foPrice) {
        this.foPrice = foPrice;
    }

    public Double getCoalPrice() {
        return coalPrice;
    }

    public void setCoalPrice(Double coalPrice) {
        this.coalPrice = coalPrice;
    }

    public Double getDieselPrice() {
        return dieselPrice;
    }

    public void setDieselPrice(Double dieselPrice) {
        this.dieselPrice = dieselPrice;
    }

    public Double getNaphthaPrice() {
        return naphthaPrice;
    }

    public void setNaphthaPrice(Double naphthaPrice) {
        this.naphthaPrice = naphthaPrice;
    }

    public Double getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(Double unitCost) {
        this.unitCost = unitCost;
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

    public Boolean getReused() {
        return reused;
    }

    public void setReused(Boolean reused) {
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
