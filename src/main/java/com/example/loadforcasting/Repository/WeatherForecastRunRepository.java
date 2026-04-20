package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.ModelVersion;
import com.example.loadforcasting.Entity.WeatherForecast;
import com.example.loadforcasting.Entity.WeatherForecastRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WeatherForecastRunRepository extends JpaRepository<WeatherForecastRun, Long> {

    Optional<WeatherForecastRun> findFirstByWeatherForecastAndModelVersionOrderByCreatedAtDesc(
            WeatherForecast weatherForecast,
            ModelVersion modelVersion
    );
}
