package com.example.loadforcasting.Entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "generation_mix_runs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_generation_mix_combo",
                        columnNames = {"load_request_id", "forecast_timestamp", "reservoir_pct"}
                )
        }
)
public class GenerationMixRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "load_request_id")
    private LoadRequest loadRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_mix_request_id")
    private GenerationMixRequestEntity generationMixRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_version_id")
    private ModelVersion modelVersion;

    @Column(name = "forecast_timestamp")
    private LocalDateTime forecastTimestamp;

    @Column(name = "estimated_daily_demand_mwh")
    private Double estimatedDailyDemandMwh;

    @Column(name = "reservoir_pct")
    private Double reservoirPct;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "run_reason", length = 100)
    private String runReason;

    @Column(name = "is_reused")
    private Boolean reused;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "total_mwh")
    private Double totalMwh;

    // Generation mix output values (MWh)
    @Column(name = "major_hydro_mwh")
    private Double majorHydroMwh;

    @Column(name = "total_coal_mwh")
    private Double totalCoalMwh;

    @Column(name = "total_thermal_mwh")
    private Double totalThermalMwh;

    @Column(name = "wind_mwh")
    private Double windMwh;

    @Column(name = "solar_mwh")
    private Double solarMwh;

    @Column(name = "mini_hydro_mwh")
    private Double miniHydroMwh;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "generationMixRun",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<CostPredictionRun> costRuns = new ArrayList<>();

    public GenerationMixRun() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLoadRequestId() {
        return loadRequest != null ? loadRequest.getId() : null;
    }

    public void setLoadRequestId(Long loadRequestId) {
    }

    public LoadRequest getLoadRequest() {
        return loadRequest;
    }

    public void setLoadRequest(LoadRequest loadRequest) {
        this.loadRequest = loadRequest;
    }

    public GenerationMixRequestEntity getGenerationMixRequest() {
        return generationMixRequest;
    }

    public void setGenerationMixRequest(GenerationMixRequestEntity generationMixRequest) {
        this.generationMixRequest = generationMixRequest;
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

    public Double getEstimatedDailyDemandMwh() {
        return estimatedDailyDemandMwh;
    }

    public void setEstimatedDailyDemandMwh(Double estimatedDailyDemandMwh) {
        this.estimatedDailyDemandMwh = estimatedDailyDemandMwh;
    }

    public Double getReservoirPct() {
        return reservoirPct;
    }

    public void setReservoirPct(Double reservoirPct) {
        this.reservoirPct = reservoirPct;
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

    public Double getTotalMwh() {
        return totalMwh;
    }

    public void setTotalMwh(Double totalMwh) {
        this.totalMwh = totalMwh;
    }

    public Double getMajorHydroMwh() {
        return majorHydroMwh;
    }

    public void setMajorHydroMwh(Double majorHydroMwh) {
        this.majorHydroMwh = majorHydroMwh;
    }

    public Double getTotalCoalMwh() {
        return totalCoalMwh;
    }

    public void setTotalCoalMwh(Double totalCoalMwh) {
        this.totalCoalMwh = totalCoalMwh;
    }

    public Double getTotalThermalMwh() {
        return totalThermalMwh;
    }

    public void setTotalThermalMwh(Double totalThermalMwh) {
        this.totalThermalMwh = totalThermalMwh;
    }

    public Double getWindMwh() {
        return windMwh;
    }

    public void setWindMwh(Double windMwh) {
        this.windMwh = windMwh;
    }

    public Double getSolarMwh() {
        return solarMwh;
    }

    public void setSolarMwh(Double solarMwh) {
        this.solarMwh = solarMwh;
    }

    public Double getMiniHydroMwh() {
        return miniHydroMwh;
    }

    public void setMiniHydroMwh(Double miniHydroMwh) {
        this.miniHydroMwh = miniHydroMwh;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<CostPredictionRun> getCostRuns() {
        return costRuns;
    }

    public void setCostRuns(List<CostPredictionRun> costRuns) {
        this.costRuns = costRuns;
    }
}
