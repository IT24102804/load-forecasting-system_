package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.CostPredictionRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CostPredictionRequestRepository extends JpaRepository<CostPredictionRequestEntity, Long> {

    Optional<CostPredictionRequestEntity> findByScenarioSignature(String scenarioSignature);
}
