package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Service.FeedbackService;
import com.example.loadforcasting.Service.LoadService;
import com.example.loadforcasting.Service.AnomalyDetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class LoadController {

    @Autowired
    private LoadService loadService;

    @Autowired
    private AnomalyDetectionService anomalyService;




    @Autowired
    private FeedbackService feedbackService;




    @PostMapping("/forecast")
    public ResponseEntity<?> forecast(@RequestBody LoadRequest request) {
        try {
            // 1. Get prediction (Assuming this saves the initial request to the DB)
            double prediction = loadService.predictAndSave(request);

            // 2. Call anomaly detection service
            Map<String, Object> anomalyResult = anomalyService.detectAnomaly(
                    request.getId(),
                    request.getTimestamp(),
                    prediction,
                    request.getTemperature(),
                    request.getHumidity(),
                    request.getPublicEvent(),
                    request.getTimestamp().getHour(),
                    request.getTimestamp().getDayOfWeek().getValue(),
                    request.getTimestamp().getMonthValue()
            );

            // 3. Update the entity with anomaly info
            request.setPredictedLoad(prediction);
            request.setIsAnomaly((Boolean) anomalyResult.get("is_anomaly"));

            // SAFELY cast the score (sometimes Python sends integers instead of doubles)
            Object scoreObj = anomalyResult.get("anomaly_score");
            if (scoreObj instanceof Number) {
                request.setAnomalyScore(((Number) scoreObj).doubleValue());
            }

            // 4. Force a database update
            loadService.updateRequestWithAnomalyInfo(request);

            // 5. Prepare and return response
            Map<String, Object> response = new HashMap<>();
            response.put("id", request.getId()); // If this is null, the feedback button will break!
            response.put("load_demand", prediction);
            response.put("is_anomaly", anomalyResult.get("is_anomaly"));
            response.put("anomaly_score", request.getAnomalyScore());
            response.put("confidence", anomalyResult.getOrDefault("confidence", 0.0));
            response.put("severity", anomalyResult.getOrDefault("severity", "NORMAL"));
            response.put("reason", anomalyResult.getOrDefault("reason", ""));
            response.put("source", anomalyResult.getOrDefault("source", "python_model"));
            response.put("model_name", anomalyResult.getOrDefault("model_name", "local_outlier_factor"));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Prediction failed: " + e.getMessage()));
        }
    }
    @PostMapping("/feedback")
    public ResponseEntity<?> storeFeedback(@RequestBody Map<String, Object> feedbackData) {
        try {

            // Validate payload before casting to avoid NullPointerException
            if (!feedbackData.containsKey("prediction_id") || !feedbackData.containsKey("user_agreed")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing prediction_id or user_agreed"));
            }

            // 1. Extract basic data
            Long predictionId = ((Number) feedbackData.get("prediction_id")).longValue();
            Boolean agreed = (Boolean) feedbackData.get("user_agreed");

            // 2. Get the original prediction request
            LoadRequest request = loadService.getRequestById(predictionId);

            if (request != null) {
                // 3. Update the LoadRequest flags
                request.setFeedbackGiven(true);
                request.setFeedbackAgreed(agreed);
                loadService.updateRequestWithAnomalyInfo(request);

                // 4. Send feedback to anomaly service for logging
                anomalyService.storeFeedback(
                        predictionId,
                        agreed,
                        request.getIsAnomaly(),
                        request.getAnomalyScore()
                );

                // 5. NEW: Create and save the detailed Feedback entity
                Feedback detailedFeedback = new Feedback();
                detailedFeedback.setPredictionId(predictionId);
                detailedFeedback.setUserName((String) feedbackData.getOrDefault("user_name", "System User"));
                detailedFeedback.setMessage((String) feedbackData.getOrDefault("message", "Feedback from Anomaly UI"));

                // Only set rating if it was provided in the payload
                if (feedbackData.containsKey("rating")) {
                    detailedFeedback.setRating(((Number) feedbackData.get("rating")).intValue());
                }

                // Assuming you have an "ANOMALY_REPORT" enum type, or just default to something
                detailedFeedback.setFeedbackType(FeedbackType.ANOMALY_FEEDBACK);

                feedbackService.saveFeedback(detailedFeedback);

                return ResponseEntity.ok(Map.of("status", "feedback completely integrated and saved"));
            } else {
                return ResponseEntity.status(404)
                        .body(Map.of("error", "Prediction not found"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to store feedback: " + e.getMessage()));
        }
    }
}
