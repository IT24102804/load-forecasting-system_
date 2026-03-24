package com.example.loadforcasting.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class AnomalyDetectionService {

    @Value("${anomaly.detection.url:http://localhost:5000}")
    private String anomalyServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Check if a predicted load value is anomalous
     */
    public Map<String, Object> detectAnomaly(double predictedLoad,
                                             double temperature,
                                             double humidity,
                                             int publicEvent,
                                             int hour,
                                             int dayOfWeek,
                                             int month) {
        Map<String, Object> result = new HashMap<>();
        result.put("is_anomaly", false);
        result.put("anomaly_score", 0.0);
        result.put("confidence", 0.0);

        try {
            // Create request body for Flask API
            Map<String, Object> request = new HashMap<>();
            request.put("load", predictedLoad);
            request.put("temperature", temperature);
            request.put("humidity", humidity);
            request.put("public_event", publicEvent);
            request.put("hour", hour);
            request.put("day_of_week", dayOfWeek);
            request.put("month", month);

            // Make POST request to Flask anomaly detection service
            Map response = restTemplate.postForObject(
                    anomalyServiceUrl + "/detect_anomaly",
                    request,
                    Map.class
            );

            if (response != null) {
                // Safely extract values from response
                Object isAnomalyObj = response.get("is_anomaly");
                result.put("is_anomaly", isAnomalyObj instanceof Boolean ? (Boolean) isAnomalyObj : false);

                Object scoreObj = response.get("anomaly_score");
                result.put("anomaly_score", scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0.0);

                Object confidenceObj = response.get("confidence");
                result.put("confidence", confidenceObj instanceof Number ? ((Number) confidenceObj).doubleValue() : 0.0);
            }

        } catch (Exception e) {
            System.err.println("Anomaly detection service error: " + e.getMessage());
            // Return default values (no anomaly) if service is down
        }

        return result;
    }

    /**
     * Send user feedback to anomaly service for logging
     */
    public void storeFeedback(Long predictionId, boolean agreed, boolean isAnomaly, double anomalyScore) {
        try {
            Map<String, Object> feedback = new HashMap<>();
            feedback.put("prediction_id", predictionId);
            feedback.put("user_agreed", agreed);
            feedback.put("is_anomaly", isAnomaly);
            feedback.put("anomaly_score", anomalyScore);

            restTemplate.postForObject(
                    anomalyServiceUrl + "/anomaly_feedback",
                    feedback,
                    Void.class
            );

        } catch (Exception e) {
            System.err.println("Failed to send feedback to anomaly service: " + e.getMessage());
        }
    }
}