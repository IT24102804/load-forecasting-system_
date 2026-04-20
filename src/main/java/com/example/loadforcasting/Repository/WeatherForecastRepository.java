package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.WeatherForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, Long> {

    Optional<WeatherForecast> findByForecastTimestamp(LocalDateTime forecastTimestamp);
}
