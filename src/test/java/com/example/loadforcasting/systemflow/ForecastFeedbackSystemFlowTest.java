package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ForecastFeedbackSystemFlowTest extends AbstractSystemFlowTest {

    @Test
    void feedbackFlow_UpdatesPredictionFlagsAndPersistsFeedbackRecord() throws Exception {
        stubLoadPrediction(1200.752);
        stubAnomalyDetection(true, 3.186, 0.8775, "HIGH",
                "Predicted load is strongly abnormal for this context.", "local_outlier_factor");
        stubAnomalyFeedbackAccepted();

        String forecastResponse = mockMvc.perform(post("/api/forecast")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", "2026-04-13T14:00:00",
                                "temperature", 30.0,
                                "humidity", 80.0,
                                "publicEvent", 0
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long predictionId = objectMapper.readTree(forecastResponse).get("id").asLong();

        mockMvc.perform(post("/api/feedback")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "prediction_id", predictionId,
                                "user_agreed", true,
                                "user_name", "System Demo User",
                                "message", "The anomaly alert looks correct.",
                                "rating", 5
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("feedback completely integrated and saved"));

        LoadRequest updated = loadRepository.findById(predictionId).orElseThrow();
        Feedback savedFeedback = feedbackRepository.findAll().get(0);

        assertTrue(Boolean.TRUE.equals(updated.getFeedbackGiven()));
        assertTrue(Boolean.TRUE.equals(updated.getFeedbackAgreed()));
        assertEquals(1, feedbackRepository.count());
        assertEquals(predictionId, savedFeedback.getPredictionId());
        assertEquals("System Demo User", savedFeedback.getUserName());
        assertEquals("The anomaly alert looks correct.", savedFeedback.getMessage());
        assertEquals(FeedbackType.ANOMALY_FEEDBACK, savedFeedback.getFeedbackType());
        assertEquals(1, anomalyFeedbackHitCount());
        assertTrue(lastAnomalyFeedbackRequestBody().contains("\"prediction_id\":" + predictionId));
    }
}
