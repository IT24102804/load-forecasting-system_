package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Repository.LoadRepository;
import com.example.loadforcasting.Service.AnomalyDetectionService;
import com.example.loadforcasting.Service.FeedbackService;
import com.example.loadforcasting.Service.LoadService;
import com.example.loadforcasting.dto.ForecastRequest;
import com.example.loadforcasting.dto.PredictionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/load")
@CrossOrigin
public class LoadForecastRestController {

    @Autowired
    private LoadService loadService;

    @Autowired
    private LoadRepository loadRepository;

    @Autowired
    private AnomalyDetectionService anomalyDetectionService;

    @Autowired
    private FeedbackService feedbackService;

    @PostMapping("/predict")
    public Map<String, Object> predict(@RequestBody PredictionRequest request) {
        return Map.of("load_demand", 2850.75, "status", "success");
    }

    @PostMapping("/forecast")
    public Map<String, Object> forecast(@RequestBody ForecastRequest request) {
        return Map.of("status", "forecast generated");
    }

    @GetMapping("/history")
    public List<Map<String, Object>> getHistory() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<LoadRequest> records = loadRepository.findTop23ByOrderByIdDesc();

        for (LoadRequest req : records) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", req.getId());
            map.put("timestamp", req.getTimestamp() != null ? req.getTimestamp().toString() : "N/A");
            map.put("temperature", req.getTemperature());
            map.put("humidity", req.getHumidity());
            map.put("public_event", req.getPublicEvent());
            map.put("predicted_load", req.getPredictedLoad());
            result.add(map);
        }
        return result;
    }

    @PostMapping("/forecast-chart")
    public Map<String, Object> getForecastChart(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Double> forecast = new ArrayList<>();

        double peakValue = 0;
        String peakHour = "";
        double lowValue = 10000;
        String lowHour = "";
        double sum = 0;

        for (int i = 0; i < 24; i++) {
            String time = String.format("%02d:00", i);
            labels.add(time);

            double baseLoad = 1500 + (Math.sin(i * Math.PI / 12) * 800) + (i == 19 || i == 20 ? 1200 : 0) + (Math.random() * 200);
            forecast.add(baseLoad);

            if (baseLoad > peakValue) {
                peakValue = baseLoad;
                peakHour = time;
            }
            if (baseLoad < lowValue) {
                lowValue = baseLoad;
                lowHour = time;
            }
            sum += baseLoad;
        }

        response.put("labels", labels);
        response.put("forecast", forecast);
        response.put("peak", Map.of("value", peakValue, "hour", peakHour));
        response.put("low", Map.of("value", lowValue, "hour", lowHour));
        response.put("average", sum / 24);

        return response;
    }

    @DeleteMapping("/delete/{id}")
    public Map<String, Object> deleteRecord(@PathVariable Long id) {
        if (loadRepository.existsById(id)) {
            feedbackService.deleteByPredictionId(id);
            anomalyDetectionService.clearAnomaliesForPrediction(id);
            loadRepository.deleteById(id);
        }
        return Map.of("status", "deleted", "id", id);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<Map<String, Object>> updateRecord(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> data) {
        LoadRequest existing = loadRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Prediction not found."));
        }

        try {
            LocalDateTime timestamp = parseTimestamp((String) data.get("timestamp"));
            double temperature = getRequiredDouble(data, "temperature");
            double humidity = getRequiredDouble(data, "humidity");
            int publicEvent = getRequiredInt(data, "publicEvent", "public_event");

            validateUpdateInput(temperature, humidity, publicEvent);

            existing.setTimestamp(timestamp);
            existing.setTemperature(temperature);
            existing.setHumidity(humidity);
            existing.setPublicEvent(publicEvent);
            existing.setFeedbackGiven(false);
            existing.setFeedbackAgreed(null);

            loadService.repredictAndUpdate(existing, true);
            double prediction = existing.getPredictedLoad();

            feedbackService.deleteByPredictionId(id);
            anomalyDetectionService.clearAnomaliesForPrediction(id);
            Map<String, Object> anomalyResult = anomalyDetectionService.detectAnomaly(
                    id,
                    timestamp,
                    prediction,
                    temperature,
                    humidity,
                    publicEvent,
                    timestamp.getHour(),
                    timestamp.getDayOfWeek().getValue(),
                    timestamp.getMonthValue()
            );

            existing.setPredictedLoad(prediction);
            existing.setIsAnomaly((Boolean) anomalyResult.get("is_anomaly"));
            Object scoreObj = anomalyResult.get("anomaly_score");
            if (scoreObj instanceof Number number) {
                existing.setAnomalyScore(number.doubleValue());
            } else {
                existing.setAnomalyScore(0.0);
            }
            loadService.updateRequestWithAnomalyInfo(existing);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "updated");
            response.put("id", id);
            response.put("predicted_load", prediction);
            response.put("timestamp", existing.getTimestamp().toString());
            response.put("temperature", existing.getTemperature());
            response.put("humidity", existing.getHumidity());
            response.put("public_event", existing.getPublicEvent());
            response.put("is_anomaly", anomalyResult.getOrDefault("is_anomaly", false));
            response.put("severity", anomalyResult.getOrDefault("severity", "NORMAL"));
            response.put("anomaly_score", existing.getAnomalyScore());
            response.put("runId", existing.getLoadForecastRunId());
            response.put("modelVersion", existing.getModelVersionLabel());
            response.put("source", existing.getSource());
            response.put("reused", existing.getRunReused());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Update failed: " + e.getMessage()));
        }
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank() || "N/A".equalsIgnoreCase(timestamp)) {
            throw new IllegalArgumentException("A valid timestamp is required.");
        }
        try {
            return LocalDateTime.parse(timestamp);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Timestamp format is invalid.");
        }
    }

    private double getRequiredDouble(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " must be a number.");
        }
        return number.doubleValue();
    }

    private int getRequiredInt(Map<String, Object> data, String preferredKey, String fallbackKey) {
        Object value = data.containsKey(preferredKey) ? data.get(preferredKey) : data.get(fallbackKey);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("publicEvent must be 0 or 1.");
        }
        return number.intValue();
    }

    private void validateUpdateInput(double temperature, double humidity, int publicEvent) {
        if (temperature < 10 || temperature > 45) {
            throw new IllegalArgumentException("Temperature must be between 10 and 45.");
        }
        if (humidity < 0 || humidity > 100) {
            throw new IllegalArgumentException("Humidity must be between 0 and 100.");
        }
        if (publicEvent != 0 && publicEvent != 1) {
            throw new IllegalArgumentException("Public event must be 0 or 1.");
        }
    }
}
