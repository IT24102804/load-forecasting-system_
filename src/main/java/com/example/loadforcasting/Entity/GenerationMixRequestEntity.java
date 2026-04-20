package com.example.loadforcasting.Entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "generation_mix_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_generation_mix_scenario_signature", columnNames = {"scenario_signature"})
})
public class GenerationMixRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_signature", nullable = false, length = 200)
    private String scenarioSignature;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "load_forecast_run_id", nullable = false)
    private LoadForecastRun loadForecastRun;

    @Column(name = "reservoir_pct", nullable = false)
    private Double reservoirPct;

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

    public String getScenarioSignature() {
        return scenarioSignature;
    }

    public void setScenarioSignature(String scenarioSignature) {
        this.scenarioSignature = scenarioSignature;
    }

    public LoadForecastRun getLoadForecastRun() {
        return loadForecastRun;
    }

    public void setLoadForecastRun(LoadForecastRun loadForecastRun) {
        this.loadForecastRun = loadForecastRun;
    }

    public Double getReservoirPct() {
        return reservoirPct;
    }

    public void setReservoirPct(Double reservoirPct) {
        this.reservoirPct = reservoirPct;
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
