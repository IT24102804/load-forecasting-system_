package com.example.loadforcasting.dto;

public class GenerationMixRequest {

    private Long predictionId;
    private Double reservoirPct;

    public Long getPredictionId() {
        return predictionId;
    }

    public void setPredictionId(Long predictionId) {
        this.predictionId = predictionId;
    }

    public Double getReservoirPct() {
        return reservoirPct;
    }

    public void setReservoirPct(Double reservoirPct) {
        this.reservoirPct = reservoirPct;
    }
}
