package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.Entity.LoadForecastRun;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Service.AnomalyDetectionService;
import com.example.loadforcasting.Service.FeedbackService;
import com.example.loadforcasting.Service.LoadService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            if (request.getTimestamp() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "timestamp is required"));
            }

            boolean forceNew = Boolean.TRUE.equals(request.getForceNew());
            double prediction = forceNew
                    ? loadService.predictAndSave(request, true)
                    : loadService.predictAndSave(request);

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

            request.setPredictedLoad(prediction);
            request.setIsAnomaly((Boolean) anomalyResult.get("is_anomaly"));
            Object scoreObj = anomalyResult.get("anomaly_score");
            if (scoreObj instanceof Number number) {
                request.setAnomalyScore(number.doubleValue());
            }
            loadService.updateRequestWithAnomalyInfo(request);

            LoadForecastRun run = loadService.getLoadForecastRunForRequest(request.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("id", request.getId());
            response.put("load_demand", prediction);
            response.put("is_anomaly", anomalyResult.get("is_anomaly"));
            response.put("anomaly_score", request.getAnomalyScore());
            response.put("confidence", anomalyResult.getOrDefault("confidence", 0.0));
            response.put("severity", anomalyResult.getOrDefault("severity", "NORMAL"));
            response.put("reason", anomalyResult.getOrDefault("reason", ""));
            response.put("source", request.getSource() != null ? request.getSource() : anomalyResult.getOrDefault("source", "python_model"));
            response.put("model_name", anomalyResult.getOrDefault("model_name", "local_outlier_factor"));
            response.put("runId", request.getLoadForecastRunId());
            response.put("modelVersion", request.getModelVersionLabel());
            response.put("reused", Boolean.TRUE.equals(request.getRunReused()));
            response.put("estimated_daily_demand_mwh",
                    request.getEstimatedDailyDemandMwh() != null
                            ? request.getEstimatedDailyDemandMwh()
                            : (run != null ? run.getEstimatedDailyDemandMwh() : null));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Prediction failed: " + e.getMessage()));
        }
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> storeFeedback(@RequestBody Map<String, Object> feedbackData,
                                           HttpSession session) {
        try {
            if (!feedbackData.containsKey("prediction_id") || !feedbackData.containsKey("user_agreed")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing prediction_id or user_agreed"));
            }

            Long predictionId = ((Number) feedbackData.get("prediction_id")).longValue();
            Boolean agreed = (Boolean) feedbackData.get("user_agreed");

            LoadRequest request = loadService.getRequestById(predictionId);
            if (request == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Prediction not found"));
            }

            request.setFeedbackGiven(true);
            request.setFeedbackAgreed(agreed);
            loadService.updateRequestWithAnomalyInfo(request);

            anomalyService.storeFeedback(
                    predictionId,
                    agreed,
                    Boolean.TRUE.equals(request.getIsAnomaly()),
                    request.getAnomalyScore() != null ? request.getAnomalyScore() : 0.0
            );

            Feedback detailedFeedback = new Feedback();
            detailedFeedback.setPredictionId(predictionId);
            detailedFeedback.setUserName((String) feedbackData.getOrDefault("user_name", "System User"));
            detailedFeedback.setUserEmail((String) feedbackData.getOrDefault("user_email", null));
            detailedFeedback.setMessage((String) feedbackData.getOrDefault("message", "Feedback from Anomaly UI"));
            if (feedbackData.containsKey("rating")) {
                detailedFeedback.setRating(((Number) feedbackData.get("rating")).intValue());
            }
            detailedFeedback.setFeedbackType(FeedbackType.ANOMALY_FEEDBACK);
            LoadForecastRun loadForecastRun = loadService.getLoadForecastRunForRequest(predictionId);
            detailedFeedback.setLoadForecastRun(loadForecastRun);

            Integer sessionUserId = (Integer) session.getAttribute("userid");
            Feedback savedFeedback;
            if (sessionUserId != null) {
                savedFeedback = feedbackService.saveFeedback(detailedFeedback, sessionUserId);
            } else {
                savedFeedback = feedbackService.saveFeedback(detailedFeedback);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "feedback completely integrated and saved");
            response.put("feedbackId", savedFeedback.getId());
            response.put("predictionId", predictionId);
            response.put("loadForecastRunId",
                    savedFeedback.getLoadForecastRun() != null ? savedFeedback.getLoadForecastRun().getId() : null);
            response.put("anomalyId",
                    savedFeedback.getAnomaly() != null ? savedFeedback.getAnomaly().getId() : null);
            response.put("feedbackType",
                    savedFeedback.getFeedbackType() != null ? savedFeedback.getFeedbackType().name() : null);
            response.put("subjectScope", savedFeedback.getSubjectScope());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to store feedback: " + e.getMessage()));
        }
    }
}
