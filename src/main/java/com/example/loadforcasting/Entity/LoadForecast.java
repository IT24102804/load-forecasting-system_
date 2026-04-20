package com.example.loadforcasting.Entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "load_forecasts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_load_forecast_input_signature", columnNames = {"input_signature"})
})
public class LoadForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "input_signature", nullable = false, length = 200)
    private String inputSignature;

    @Column(name = "forecast_timestamp", nullable = false)
    private LocalDateTime forecastTimestamp;

    @Column(name = "temperature_input", nullable = false)
    private double temperatureInput;

    @Column(name = "humidity_input", nullable = false)
    private double humidityInput;

    @Column(name = "public_event_flag", nullable = false)
    private int publicEventFlag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weather_forecast_run_id")
    private WeatherForecastRun weatherForecastRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedByUser;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
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

    public String getInputSignature() {
        return inputSignature;
    }

    public void setInputSignature(String inputSignature) {
        this.inputSignature = inputSignature;
    }

    public LocalDateTime getForecastTimestamp() {
        return forecastTimestamp;
    }

    public void setForecastTimestamp(LocalDateTime forecastTimestamp) {
        this.forecastTimestamp = forecastTimestamp;
    }

    public double getTemperatureInput() {
        return temperatureInput;
    }

    public void setTemperatureInput(double temperatureInput) {
        this.temperatureInput = temperatureInput;
    }

    public double getHumidityInput() {
        return humidityInput;
    }

    public void setHumidityInput(double humidityInput) {
        this.humidityInput = humidityInput;
    }

    public int getPublicEventFlag() {
        return publicEventFlag;
    }

    public void setPublicEventFlag(int publicEventFlag) {
        this.publicEventFlag = publicEventFlag;
    }

    public WeatherForecastRun getWeatherForecastRun() {
        return weatherForecastRun;
    }

    public void setWeatherForecastRun(WeatherForecastRun weatherForecastRun) {
        this.weatherForecastRun = weatherForecastRun;
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

    public User getUpdatedByUser() {
        return updatedByUser;
    }

    public void setUpdatedByUser(User updatedByUser) {
        this.updatedByUser = updatedByUser;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
