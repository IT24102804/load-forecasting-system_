package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.Entity.LoadData;
import com.example.loadforcasting.Repository.AnomalyRepository;
import com.example.loadforcasting.Repository.LoadDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AnomalyDetectionService {

    @Value("${anomaly.service.url:http://localhost:5002}")
    private String anomalyServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private AnomalyRepository anomalyRepository;

    @Autowired
    private LoadDataRepository loadDataRepository;

    // =====================================================
    // MAIN DETECTION — called from LoadController
    // Tries Python Flask first, falls back to rule-based
    // =====================================================
    public Map<String, Object> detectAnomaly(Long predictionId, LocalDateTime forecastTimestamp,
                                             double predictedLoad, double temperature,
                                             double humidity, int publicEvent,
                                             int hour, int dayOfWeek, int month) {
        Map<String, Object> result;

        try {
            // Try Python Flask API first
            Map<String, Object> request = new HashMap<>();

            request.put("load", predictedLoad);
            request.put("temp", temperature);
            request.put("humidity", humidity);
            request.put("hour", hour);
            request.put("day", dayOfWeek);
            request.put("month", month);
            request.put("event", publicEvent);
            request.put("season", deriveSeason(month));

            Map response = restTemplate.postForObject(
                    anomalyServiceUrl + "/detect_anomaly", request, Map.class);

            result = new HashMap<>();
            if (response != null) {
                Object isAnomalyObj = response.get("is_anomaly");
                boolean isAnomaly = isAnomalyObj instanceof Boolean ? (Boolean) isAnomalyObj : false;
                result.put("is_anomaly", isAnomaly);

                Object scoreObj = response.get("anomaly_score");
                result.put("anomaly_score", scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0.0);

                Object confidenceObj = response.get("confidence");
                result.put("confidence", confidenceObj instanceof Number ? ((Number) confidenceObj).doubleValue() : 0.0);

                result.put("source", "python_model");
                result.put("model_name", response.getOrDefault("model_name", "local_outlier_factor"));
                result.put("severity", response.getOrDefault("severity", isAnomaly ? "LOW" : "NORMAL"));
                result.put("reason", response.getOrDefault(
                        "reason",
                        isAnomaly ? "Anomaly detected by the deployed LOF detector." : "Load behavior matches historical patterns."
                ));

                System.out.println("🚨 Python Anomaly AI Result: " + result);
            } else {
                result = ruleBased(predictedLoad, temperature, humidity, hour);
            }

        } catch (Exception e) {
            System.out.println("Python service offline, using rule-based detection.");
            result = ruleBased(predictedLoad, temperature, humidity, hour);
        }

        if (Boolean.TRUE.equals(result.get("is_anomaly"))) {
            saveAnomaly(predictionId, forecastTimestamp, predictedLoad, temperature, humidity, publicEvent,
                    hour, dayOfWeek, month, result);
        }

        return result;
    }

    @Transactional
    public void clearAnomaliesForPrediction(Long predictionId) {
        if (predictionId != null) {
            anomalyRepository.deleteByPredictionId(predictionId);
        }
    }

    // =====================================================
    // RULE-BASED FALLBACK (works without Python)
    // =====================================================
    private Map<String, Object> ruleBased(double load, double temperature,
                                          double humidity, int hour) {
        Map<String, Object> result = new HashMap<>();

        List<LoadData> historicalData = loadDataRepository.findAll();
        double[] stats = getHourlyStats(historicalData, hour);
        double mean = stats[0];
        double stdDev = stats[1];

        double zScore = (stdDev > 0) ? Math.abs(load - mean) / stdDev : 0;
        double anomalyScore = Math.min(zScore / 3.0, 1.0);

        boolean isAnomaly = false;
        String severity = "LOW";
        String reason = "";

        if (zScore >= 2.5) {
            isAnomaly = true;
            if (zScore >= 4.0) {
                severity = "HIGH";
                reason = String.format("Load (%.1f kW) is %.1fσ from hourly mean (%.1f kW). Critical.", load, zScore, mean);
            } else if (zScore >= 3.0) {
                severity = "MEDIUM";
                reason = String.format("Load (%.1f kW) deviates %.1fσ from hourly average (%.1f kW).", load, zScore, mean);
            } else {
                severity = "LOW";
                reason = String.format("Mild anomaly: load (%.1f kW) is %.1fσ from mean (%.1f kW).", load, zScore, mean);
            }
        } else if (temperature > 35 && load < mean * 0.7) {
            isAnomaly = true;
            severity = "MEDIUM";
            reason = String.format("Low load (%.1f kW) during high temp (%.1f°C). Possible outage.", load, temperature);
            anomalyScore = 0.65;
        }

        result.put("is_anomaly", isAnomaly);
        result.put("anomaly_score", anomalyScore);
        result.put("confidence", Math.min(0.99, 0.55 + (anomalyScore * 0.25)));
        result.put("severity", severity);
        result.put("reason", reason);
        result.put("source", "rule_based");
        result.put("model_name", "hour_zscore_fallback");
        return result;
    }

    private void saveAnomaly(Long predictionId, LocalDateTime forecastTimestamp,
                             double load, double temperature, double humidity, int publicEvent,
                             int hour, int dayOfWeek, int month, Map<String, Object> result) {
        try {
            Anomaly anomaly = new Anomaly();
            anomaly.setPredictionId(predictionId);
            anomaly.setTimestamp(forecastTimestamp != null ? forecastTimestamp : LocalDateTime.now());
            anomaly.setLoadDemand(load);
            anomaly.setTemperature(temperature);
            anomaly.setHumidity(humidity);
            anomaly.setPublicEvent(publicEvent);
            anomaly.setHourOfDay(hour);
            anomaly.setDayOfWeek(dayOfWeek);
            anomaly.setMonth(month);
            anomaly.setAnomalyScore(((Number) result.getOrDefault("anomaly_score", 0.0)).doubleValue());
            anomaly.setConfidence(((Number) result.getOrDefault("confidence", 0.0)).doubleValue());
            anomaly.setSeverity((String) result.getOrDefault("severity", "LOW"));
            anomaly.setReason((String) result.getOrDefault("reason", "Anomaly detected"));
            anomaly.setStatus("OPEN");
            anomalyRepository.save(anomaly);
        } catch (Exception e) {
            System.err.println("Could not save anomaly record: " + e.getMessage());
        }
    }

    private int deriveSeason(int month) {
        return switch (month) {
            case 3, 4, 5 -> 0;
            case 6, 7, 8 -> 1;
            case 9, 10, 11 -> 2;
            default -> 3;
        };
    }

    // =====================================================
    // SERVICE METHODS (used by AnomalyController for UI)
    // =====================================================
    public List<Anomaly> getAllAnomalies() {
        return anomalyRepository.findAllByOrderByDetectedAtDesc();
    }

    public List<Anomaly> getAnomaliesBySeverity(String severity) {
        return anomalyRepository.findBySeverityOrderByDetectedAtDesc(severity);
    }

    public List<Anomaly> getAnomaliesByStatus(String status) {
        return anomalyRepository.findByStatusOrderByDetectedAtDesc(status);
    }

    public Optional<Anomaly> getAnomalyById(Long id) {
        return anomalyRepository.findById(id);
    }

    public Anomaly acknowledgeAnomaly(Long id) {
        Anomaly a = anomalyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anomaly not found: " + id));
        a.setStatus("ACKNOWLEDGED");
        return anomalyRepository.save(a);
    }

    public Anomaly resolveAnomaly(Long id, String note) {
        Anomaly a = anomalyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anomaly not found: " + id));
        a.setStatus("RESOLVED");
        a.setResolutionNote(note);
        a.setResolvedAt(LocalDateTime.now());
        return anomalyRepository.save(a);
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAnomalies",  anomalyRepository.count());
        stats.put("highCount",       anomalyRepository.countBySeverity("HIGH"));
        stats.put("mediumCount",     anomalyRepository.countBySeverity("MEDIUM"));
        stats.put("lowCount",        anomalyRepository.countBySeverity("LOW"));
        stats.put("openCount",       anomalyRepository.countByStatus("OPEN"));
        stats.put("resolvedCount",   anomalyRepository.countByStatus("RESOLVED"));
        stats.put("todayCount",      anomalyRepository.countTodaysAnomalies());
        stats.put("recentAnomalies", anomalyRepository.findTop10ByOrderByDetectedAtDesc());
        return stats;
    }

    // =====================================================
    // FEEDBACK LOGGING (called from LoadController)
    // =====================================================
    public void storeFeedback(Long predictionId, boolean agreed, boolean isAnomaly, double anomalyScore) {
        try {
            Map<String, Object> feedback = new HashMap<>();
            feedback.put("prediction_id", predictionId);
            feedback.put("user_agreed", agreed);
            feedback.put("is_anomaly", isAnomaly);
            feedback.put("anomaly_score", anomalyScore);
            restTemplate.postForObject(anomalyServiceUrl + "/anomaly_feedback", feedback, Void.class);
        } catch (Exception e) {
            System.out.println("Feedback log skipped (Python offline): " + e.getMessage());
        }
    }

    // =====================================================
    // PRIVATE HELPER
    // =====================================================
    private double[] getHourlyStats(List<LoadData> data, int hour) {
        List<Double> loads = new ArrayList<>();
        for (LoadData d : data) {
            if (d.getHourOfDay() == hour) loads.add(d.getLoadDemand());
        }
        if (loads.isEmpty()) return new double[]{3500.0, 800.0};

        double mean = loads.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = loads.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        return new double[]{mean, Math.sqrt(variance)};
    }
}
