package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.Entity.AnomalyStatusEvent;
import com.example.loadforcasting.Entity.LoadData;
import com.example.loadforcasting.Entity.LoadForecastRun;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Entity.ModelVersion;
import com.example.loadforcasting.Entity.User;
import com.example.loadforcasting.Repository.AnomalyRepository;
import com.example.loadforcasting.Repository.AnomalyStatusEventRepository;
import com.example.loadforcasting.Repository.LoadDataRepository;
import com.example.loadforcasting.Repository.LoadRepository;
import com.example.loadforcasting.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class AnomalyDetectionService {

    @Value("${anomaly.service.url:http://localhost:5002}")
    private String anomalyServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private AnomalyRepository anomalyRepository;

    @Autowired
    private AnomalyStatusEventRepository anomalyStatusEventRepository;

    @Autowired
    private LoadDataRepository loadDataRepository;

    @Autowired
    private LoadRepository loadRepository;

    @Autowired
    private LoadService loadService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ModelVersionService modelVersionService;

    public Map<String, Object> detectAnomaly(Long predictionId, LocalDateTime forecastTimestamp,
                                             double predictedLoad, double temperature,
                                             double humidity, int publicEvent,
                                             int hour, int dayOfWeek, int month) {
        Map<String, Object> result;

        try {
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
                result = new HashMap<>();
                boolean isAnomaly = parseBoolean(response.get("is_anomaly"));
                result.put("is_anomaly", isAnomaly);
                result.put("anomaly_score", asDouble(response.getOrDefault("anomaly_score", 0.0)));
                result.put("confidence", asDouble(response.getOrDefault("confidence", 0.0)));
                result.put("source", response.getOrDefault("source", "python_model"));
                result.put("model_name", response.getOrDefault("model_name", "local_outlier_factor"));
                result.put("severity", response.getOrDefault("severity", isAnomaly ? "LOW" : "NORMAL"));
                result.put("reason", response.getOrDefault(
                        "reason",
                        isAnomaly ? "Anomaly detected by the deployed detector." : "Load behavior matches historical patterns."
                ));
            } else {
                result = ruleBased(predictedLoad, temperature, humidity, hour);
            }
        } catch (Exception e) {
            result = ruleBased(predictedLoad, temperature, humidity, hour);
        }

        if (Boolean.TRUE.equals(result.get("is_anomaly"))) {
            saveAnomaly(predictionId, forecastTimestamp, predictedLoad, temperature, humidity, publicEvent,
                    hour, dayOfWeek, month, result);
        }

        return result;
    }

    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        if (value instanceof String s) {
            String normalized = s.trim().toLowerCase(Locale.ROOT);
            return normalized.equals("true") || normalized.equals("yes") || normalized.equals("1");
        }
        return false;
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    @Transactional
    public void clearAnomaliesForPrediction(Long predictionId) {
        if (predictionId != null) {
            anomalyRepository.deleteByPredictionId(predictionId);
        }
    }

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
                reason = String.format(Locale.ROOT, "Load (%.1f kW) is %.1fσ from hourly mean (%.1f kW). Critical.", load, zScore, mean);
            } else if (zScore >= 3.0) {
                severity = "MEDIUM";
                reason = String.format(Locale.ROOT, "Load (%.1f kW) deviates %.1fσ from hourly average (%.1f kW).", load, zScore, mean);
            } else {
                severity = "LOW";
                reason = String.format(Locale.ROOT, "Mild anomaly: load (%.1f kW) is %.1fσ from mean (%.1f kW).", load, zScore, mean);
            }
        } else if (temperature > 35 && load < mean * 0.7) {
            isAnomaly = true;
            severity = "MEDIUM";
            reason = String.format(Locale.ROOT, "Low load (%.1f kW) during high temp (%.1f°C). Possible outage.", load, temperature);
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
            ModelVersion modelVersion = modelVersionService.resolveCurrent(
                    ModelVersionService.MODULE_ANOMALY,
                    String.valueOf(result.getOrDefault("model_name", "anomaly_detector"))
            );

            LoadForecastRun loadForecastRun = loadService.getLoadForecastRunForRequest(predictionId);
            Anomaly anomaly = loadForecastRun != null
                    ? anomalyRepository.findFirstByLoadForecastRun_IdAndModelVersion_IdOrderByDetectedAtDesc(
                                    loadForecastRun.getId(),
                                    modelVersion.getId()
                            )
                            .orElseGet(Anomaly::new)
                    : new Anomaly();

            anomaly.setPredictionId(predictionId);
            anomaly.setLoadForecastRun(loadForecastRun);
            anomaly.setModelVersion(modelVersion);
            anomaly.setTimestamp(forecastTimestamp != null ? forecastTimestamp : LocalDateTime.now());
            anomaly.setLoadDemand(load);
            anomaly.setTemperature(temperature);
            anomaly.setHumidity(humidity);
            anomaly.setPublicEvent(publicEvent);
            anomaly.setHourOfDay(hour);
            anomaly.setDayOfWeek(dayOfWeek);
            anomaly.setMonth(month);
            anomaly.setAnomalyScore(asDouble(result.getOrDefault("anomaly_score", 0.0)));
            anomaly.setConfidence(asDouble(result.getOrDefault("confidence", 0.0)));
            anomaly.setSeverity(String.valueOf(result.getOrDefault("severity", "LOW")));
            anomaly.setReason(String.valueOf(result.getOrDefault("reason", "Anomaly detected")));
            anomaly.setStatus("OPEN");
            anomaly.setSource(String.valueOf(result.getOrDefault("source", "python_model")));
            anomaly.setModelName(String.valueOf(result.getOrDefault("model_name", "anomaly_detector")));
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

    @Transactional
    public Anomaly acknowledgeAnomaly(Long id) {
        return acknowledgeAnomaly(id, null);
    }

    @Transactional
    public Anomaly acknowledgeAnomaly(Long id, Integer userId) {
        Anomaly anomaly = anomalyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anomaly not found: " + id));
        String oldStatus = anomaly.getStatus();
        anomaly.setStatus("ACKNOWLEDGED");
        anomaly.setAcknowledgedAt(LocalDateTime.now());
        resolveUser(userId).ifPresent(anomaly::setAcknowledgedByUser);
        Anomaly saved = anomalyRepository.save(anomaly);
        createStatusEvent(saved, oldStatus, "ACKNOWLEDGED", "Acknowledged by operator.", userId);
        return saved;
    }

    @Transactional
    public Anomaly resolveAnomaly(Long id, String note) {
        return resolveAnomaly(id, note, null);
    }

    @Transactional
    public Anomaly resolveAnomaly(Long id, String note, Integer userId) {
        Anomaly anomaly = anomalyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anomaly not found: " + id));
        String oldStatus = anomaly.getStatus();
        anomaly.setStatus("RESOLVED");
        anomaly.setResolutionNote(note);
        anomaly.setResolvedAt(LocalDateTime.now());
        resolveUser(userId).ifPresent(anomaly::setResolvedByUser);
        Anomaly saved = anomalyRepository.save(anomaly);
        createStatusEvent(saved, oldStatus, "RESOLVED", note, userId);
        return saved;
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAnomalies", anomalyRepository.count());
        stats.put("highCount", anomalyRepository.countBySeverity("HIGH"));
        stats.put("mediumCount", anomalyRepository.countBySeverity("MEDIUM"));
        stats.put("lowCount", anomalyRepository.countBySeverity("LOW"));
        stats.put("openCount", anomalyRepository.countByStatus("OPEN"));
        stats.put("resolvedCount", anomalyRepository.countByStatus("RESOLVED"));
        stats.put("todayCount", anomalyRepository.countTodaysAnomalies());
        stats.put("recentAnomalies", anomalyRepository.findTop10ByOrderByDetectedAtDesc());
        return stats;
    }

    public void storeFeedback(Long predictionId, boolean agreed, boolean isAnomaly, double anomalyScore) {
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("prediction_id", predictionId);
        feedback.put("user_agreed", agreed);
        feedback.put("is_anomaly", isAnomaly);
        feedback.put("anomaly_score", anomalyScore);

        // This relay is only a best-effort side log for the Python service.
        // Run it off the request thread so UI feedback is not blocked on local HTTP latency.
        CompletableFuture.runAsync(() -> postFeedbackToPython(feedback));
    }

    private void postFeedbackToPython(Map<String, Object> feedback) {
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(1500);
            factory.setReadTimeout(1500);
            RestTemplate feedbackRestTemplate = new RestTemplate(factory);
            feedbackRestTemplate.postForObject(anomalyServiceUrl + "/anomaly_feedback", feedback, Void.class);
        } catch (Exception e) {
            System.out.println("Feedback log skipped (Python offline): " + e.getMessage());
        }
    }

    private void createStatusEvent(Anomaly anomaly, String oldStatus, String newStatus, String note, Integer userId) {
        resolveUser(userId).ifPresent(user -> {
            AnomalyStatusEvent event = new AnomalyStatusEvent();
            event.setAnomaly(anomaly);
            event.setOldStatus(oldStatus == null ? "OPEN" : oldStatus);
            event.setNewStatus(newStatus);
            event.setNote(note);
            event.setActedByUser(user);
            anomalyStatusEventRepository.save(event);
        });
    }

    private Optional<User> resolveUser(Integer userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId);
    }

    private double[] getHourlyStats(List<LoadData> data, int hour) {
        List<Double> loads = new ArrayList<>();
        for (LoadData d : data) {
            if (d.getHourOfDay() == hour) {
                loads.add(d.getLoadDemand());
            }
        }
        if (loads.isEmpty()) {
            return new double[]{3500.0, 800.0};
        }

        double mean = loads.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = loads.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        return new double[]{mean, Math.sqrt(variance)};
    }
}
