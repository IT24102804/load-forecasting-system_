package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.LoadForecast;
import com.example.loadforcasting.Entity.LoadForecastRun;
import com.example.loadforcasting.Entity.ModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoadForecastRunRepository extends JpaRepository<LoadForecastRun, Long> {

    Optional<LoadForecastRun> findFirstByLoadForecastAndModelVersionOrderByCreatedAtDesc(
            LoadForecast loadForecast,
            ModelVersion modelVersion
    );
}
