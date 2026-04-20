package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.Anomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {

    // All anomalies newest first
    List<Anomaly> findAllByOrderByDetectedAtDesc();

    // Filter by severity
    List<Anomaly> findBySeverityOrderByDetectedAtDesc(String severity);

    // Filter by status
    List<Anomaly> findByStatusOrderByDetectedAtDesc(String status);

    // Count by severity (for dashboard cards)
    long countBySeverity(String severity);

    // Count by status
    long countByStatus(String status);

    // All unresolved anomalies
    List<Anomaly> findByStatusNotOrderByDetectedAtDesc(String status);

    void deleteByPredictionId(Long predictionId);

    // Recent 10 for dashboard preview
    List<Anomaly> findTop10ByOrderByDetectedAtDesc();

    // Stats query
    @Query("SELECT COUNT(a) FROM Anomaly a WHERE a.detectedAt >= CURRENT_DATE")
    long countTodaysAnomalies();

    Optional<Anomaly> findFirstByLoadForecastRun_IdAndModelVersion_IdOrderByDetectedAtDesc(Long loadForecastRunId, Long modelVersionId);

    Optional<Anomaly> findFirstByLoadForecastRun_IdOrderByDetectedAtDesc(Long loadForecastRunId);

    Optional<Anomaly> findFirstByPredictionIdOrderByDetectedAtDesc(Long predictionId);
}
