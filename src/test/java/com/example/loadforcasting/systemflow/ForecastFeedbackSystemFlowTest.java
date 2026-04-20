package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ForecastFeedbackSystemFlowTest extends AbstractSystemFlowTest {

    @Test
    void feedbackFlow_UpdatesPredictionFlagsAndPersistsFeedbackRecord() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(1).withNano(0);
        String tsStr = ts.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        stubLoadPrediction(1200.752);
        stubAnomalyDetection(true, 3.186, 0.8775, "HIGH",
                "Predicted load is strongly abnormal for this context.", "local_outlier_factor");
        stubAnomalyFeedbackAccepted();

        String forecastResponse = mockMvc.perform(post("/api/forecast")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", tsStr,
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
                .andExpect(jsonPath("$.status").value("feedback completely integrated and saved"))
                .andExpect(jsonPath("$.feedbackId").isNumber())
                .andExpect(jsonPath("$.predictionId").value(predictionId))
                .andExpect(jsonPath("$.loadForecastRunId").isNumber())
                .andExpect(jsonPath("$.anomalyId").isNumber())
                .andExpect(jsonPath("$.feedbackType").value("ANOMALY_FEEDBACK"))
                .andExpect(jsonPath("$.subjectScope").value("ANOMALY"));

        LoadRequest updated = loadRepository.findById(predictionId).orElseThrow();
        Feedback savedFeedback = feedbackRepository.findAll().get(0);

        assertTrue(Boolean.TRUE.equals(updated.getFeedbackGiven()));
        assertTrue(Boolean.TRUE.equals(updated.getFeedbackAgreed()));
        assertEquals(1, feedbackRepository.count());
        assertEquals(predictionId, savedFeedback.getPredictionId());
        assertEquals("System Demo User", savedFeedback.getUserName());
        assertEquals("The anomaly alert looks correct.", savedFeedback.getMessage());
        assertEquals(FeedbackType.ANOMALY_FEEDBACK, savedFeedback.getFeedbackType());
        assertEquals("ANOMALY", savedFeedback.getSubjectScope());
        assertNotNull(savedFeedback.getLoadForecastRun());
        assertNotNull(savedFeedback.getAnomaly());
        for (int i = 0; i < 20 && anomalyFeedbackHitCount() == 0; i++) {
            Thread.sleep(100);
        }
        assertEquals(1, anomalyFeedbackHitCount());
        assertTrue(lastAnomalyFeedbackRequestBody().contains("\"prediction_id\":" + predictionId));

        mockMvc.perform(get("/feedback/submit")
                        .param("predictionId", String.valueOf(predictionId))
                        .param("anomalyId", String.valueOf(savedFeedback.getAnomaly().getId()))
                        .param("feedbackType", "ANOMALY_FEEDBACK"))
                .andExpect(status().isOk())
                .andExpect(view().name("feedback/submit"))
                .andExpect(model().attribute("anomalyContext", true))
                .andExpect(model().attribute("contextPredictionId", predictionId));

        mockMvc.perform(post("/feedback/submit")
                        .param("predictionId", String.valueOf(predictionId))
                        .param("anomalyId", String.valueOf(savedFeedback.getAnomaly().getId()))
                        .param("userName", "System Demo User")
                        .param("userEmail", "demo@example.com")
                        .param("feedbackType", "GENERAL")
                        .param("message", "Detailed anomaly review note for the operations team.")
                        .param("rating", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feedback/my-feedback?email=demo@example.com"));

        Feedback detailedFeedback = feedbackRepository.findAll().stream()
                .filter(feedback -> "Detailed anomaly review note for the operations team.".equals(feedback.getMessage()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, feedbackRepository.count());
        assertEquals(FeedbackType.ANOMALY_FEEDBACK, detailedFeedback.getFeedbackType());
        assertEquals(predictionId, detailedFeedback.getPredictionId());
        assertEquals("ANOMALY", detailedFeedback.getSubjectScope());
        assertNotNull(detailedFeedback.getAnomaly());
    }
}
