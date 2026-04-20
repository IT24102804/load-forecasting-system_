package com.example.loadforcasting.dto;

public class CostPredictionRequest {

    private Long runId;

    private Double foPrice;

    private Double coalPrice;

    private Double dieselPrice;

    private Double naphthaPrice;

    private Boolean forceNew;

    public CostPredictionRequest() {
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
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

    public Boolean getForceNew() {
        return forceNew;
    }

    public void setForceNew(Boolean forceNew) {
        this.forceNew = forceNew;
    }
}
