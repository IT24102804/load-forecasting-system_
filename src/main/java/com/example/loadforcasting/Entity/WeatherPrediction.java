package com.example.loadforcasting.Entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "weather_predictions")
public class WeatherPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prediction_date", nullable = false)
    private LocalDate predictionDate;

    @Column(name = "prediction_time", nullable = false)
    private LocalTime predictionTime;

    @Column(name = "temperature", nullable = false)
    private Double temperature;

    @Column(name = "humidity", nullable = false)
    private Double humidity;

    @Column(name = "wind_speed", nullable = false)
    private Double windSpeed;

    @Column(name = "rainfall", nullable = false)
    private Double rainfall;

    @Column(name = "solar_irradiance", nullable = false)
    private Double solarIrradiance;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Default Constructor
    public WeatherPrediction() {}

    // Parameterized Constructor
    public WeatherPrediction(LocalDate predictionDate, LocalTime predictionTime,
                             Double temperature, Double humidity, Double windSpeed,
                             Double rainfall, Double solarIrradiance) {
        this.predictionDate = predictionDate;
        this.predictionTime = predictionTime;
        this.temperature = temperature;
        this.humidity = humidity;
        this.windSpeed = windSpeed;
        this.rainfall = rainfall;
        this.solarIrradiance = solarIrradiance;
    }

    // Getters
    public Long getId() { return id; }
    public LocalDate getPredictionDate() { return predictionDate; }
    public LocalTime getPredictionTime() { return predictionTime; }
    public Double getTemperature() { return temperature; }
    public Double getHumidity() { return humidity; }
    public Double getWindSpeed() { return windSpeed; }
    public Double getRainfall() { return rainfall; }
    public Double getSolarIrradiance() { return solarIrradiance; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setPredictionDate(LocalDate predictionDate) { this.predictionDate = predictionDate; }
    public void setPredictionTime(LocalTime predictionTime) { this.predictionTime = predictionTime; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public void setHumidity(Double humidity) { this.humidity = humidity; }
    public void setWindSpeed(Double windSpeed) { this.windSpeed = windSpeed; }
    public void setRainfall(Double rainfall) { this.rainfall = rainfall; }
    public void setSolarIrradiance(Double solarIrradiance) { this.solarIrradiance = solarIrradiance; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}