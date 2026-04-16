package com.example.loadforcasting.dto;

import java.time.LocalDateTime;

public record WeatherPredictionResult(
        LocalDateTime predictionDateTime,
        double temperature,
        double humidity,
        double windSpeed,
        double rainfall,
        double solarIrradiance,
        String source
) {
}
