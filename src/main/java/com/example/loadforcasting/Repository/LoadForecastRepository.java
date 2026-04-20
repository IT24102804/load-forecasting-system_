package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.LoadForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoadForecastRepository extends JpaRepository<LoadForecast, Long> {

    Optional<LoadForecast> findByInputSignature(String inputSignature);
}
