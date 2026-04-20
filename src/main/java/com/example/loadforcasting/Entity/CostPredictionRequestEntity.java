package com.example.loadforcasting.Entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cost_prediction_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cost_prediction_scenario_signature", columnNames = {"scenario_signature"})
})
public class CostPredictionRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_signature", nullable = false, length = 255)
    private String scenarioSignature;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generation_mix_run_id", nullable = false)
    private GenerationMixRun generationMixRun;

    @Column(name = "fo_price", nullable = false)
    private Double foPrice;

    @Column(name = "coal_price", nullable = false)
    private Double coalPrice;

    @Column(name = "diesel_price", nullable = false)
    private Double dieselPrice;

    @Column(name = "naphtha_price", nullable = false)
    private Double naphthaPrice;

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

    public GenerationMixRun getGenerationMixRun() {
        return generationMixRun;
    }

    public void setGenerationMixRun(GenerationMixRun generationMixRun) {
        this.generationMixRun = generationMixRun;
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
