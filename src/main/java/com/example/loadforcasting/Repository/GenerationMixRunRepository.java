package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.GenerationMixRun;
import com.example.loadforcasting.Entity.GenerationMixRequestEntity;
import com.example.loadforcasting.Entity.ModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GenerationMixRunRepository extends JpaRepository<GenerationMixRun, Long> {

    List<GenerationMixRun> findAllByOrderByCreatedAtDesc();

    Optional<GenerationMixRun> findFirstByLoadRequest_IdAndForecastTimestampAndReservoirPctOrderByCreatedAtDesc(
            Long loadRequestId,
            LocalDateTime forecastTimestamp,
            Double reservoirPct
    );

    Optional<GenerationMixRun> findFirstByLoadRequest_TimestampAndLoadRequest_TemperatureAndLoadRequest_HumidityAndLoadRequest_PublicEventAndReservoirPctOrderByCreatedAtDesc(
            LocalDateTime timestamp,
            double temperature,
            double humidity,
            int publicEvent,
            Double reservoirPct
    );

    Optional<GenerationMixRun> findFirstByLoadRequest_TimestampAndLoadRequest_TemperatureAndLoadRequest_HumidityAndLoadRequest_PublicEventOrderByCreatedAtDesc(
            LocalDateTime timestamp,
            double temperature,
            double humidity,
            int publicEvent
    );

    Optional<GenerationMixRun> findFirstByLoadRequest_IdAndForecastTimestampOrderByCreatedAtDesc(
            Long loadRequestId,
            LocalDateTime forecastTimestamp
    );

    Optional<GenerationMixRun> findFirstByLoadRequest_IdOrderByCreatedAtDesc(
            Long loadRequestId
    );

    Optional<GenerationMixRun> findFirstByGenerationMixRequestAndModelVersionOrderByCreatedAtDesc(
            GenerationMixRequestEntity generationMixRequest,
            ModelVersion modelVersion
    );
}
