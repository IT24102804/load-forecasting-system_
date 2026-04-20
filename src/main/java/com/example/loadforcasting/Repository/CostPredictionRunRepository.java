package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.CostPredictionRun;
import com.example.loadforcasting.Entity.CostPredictionRequestEntity;
import com.example.loadforcasting.Entity.ModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CostPredictionRunRepository extends JpaRepository<CostPredictionRun, Long> {

    List<CostPredictionRun> findAllByOrderByCreatedAtDesc();

    List<CostPredictionRun> findAllByForecastTimestampBetweenOrderByForecastTimestampAsc(
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<CostPredictionRun> findFirstByGenerationMixRun_IdOrderByCreatedAtDesc(
            Long generationMixResultId
    );

    Optional<CostPredictionRun> findFirstByGenerationMixRun_IdAndFoPriceAndCoalPriceAndDieselPriceAndNaphthaPriceOrderByCreatedAtDesc(
            Long generationMixResultId,
            Double foPrice,
            Double coalPrice,
            Double dieselPrice,
            Double naphthaPrice
    );

    Optional<CostPredictionRun> findFirstByCostPredictionRequestAndModelVersionOrderByCreatedAtDesc(
            CostPredictionRequestEntity costPredictionRequest,
            ModelVersion modelVersion
    );

    List<CostPredictionRun> findTop200ByOrderByForecastTimestampAsc();
}
