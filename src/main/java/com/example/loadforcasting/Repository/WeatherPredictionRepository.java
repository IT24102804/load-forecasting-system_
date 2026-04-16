package com.example.loadforcasting.Repository;


import com.example.loadforcasting.Entity.WeatherPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WeatherPredictionRepository extends JpaRepository<WeatherPrediction, Long> {

    List<WeatherPrediction> findAllByOrderByCreatedAtDesc();

    List<WeatherPrediction> findByPredictionDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT COUNT(w) FROM WeatherPrediction w")
    Long getTotalPredictions();

    @Query("SELECT AVG(w.temperature) FROM WeatherPrediction w")
    Double getAverageTemperature();

    @Query("SELECT AVG(w.humidity) FROM WeatherPrediction w")
    Double getAverageHumidity();

    @Query("SELECT MAX(w.temperature) FROM WeatherPrediction w")
    Double getMaxTemperature();

    @Query("SELECT MIN(w.temperature) FROM WeatherPrediction w")
    Double getMinTemperature();

    @Query("SELECT SUM(w.rainfall) FROM WeatherPrediction w")
    Double getTotalRainfall();

    @Query("SELECT FUNCTION('MONTH', w.predictionDate) as month, AVG(w.temperature) as avgTemp FROM WeatherPrediction w GROUP BY FUNCTION('MONTH', w.predictionDate) ORDER BY month")
    List<Object[]> getMonthlyTemperatureAverages();

    // Delete by ID
    @Transactional
    @Modifying
    @Query("DELETE FROM WeatherPrediction w WHERE w.id = :id")
    void deleteById(@Param("id") Long id);

    // Check if exists by ID
    boolean existsById(Long id);
}