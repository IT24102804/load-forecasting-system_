package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.GenerationMixRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenerationMixRequestRepository extends JpaRepository<GenerationMixRequestEntity, Long> {

    Optional<GenerationMixRequestEntity> findByScenarioSignature(String scenarioSignature);
}
