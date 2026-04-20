package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ForecastHistoryUpdateSystemFlowTest extends AbstractSystemFlowTest {

    @Test
    void updateHistoryRow_RecalculatesPredictionAndPersistsEditedValues() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(1).withNano(0);
        stubLoadPrediction(1205.842);
        stubAnomalyDetection(false, 0.182, 0.58, "NORMAL",
                "Load behavior matches historical patterns.", "local_outlier_factor");

        String forecastResponse = mockMvc.perform(post("/api/forecast")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", ts.toString(),
                                "temperature", 28.0,
                                "humidity", 80.0,
                                "publicEvent", 1
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long predictionId = objectMapper.readTree(forecastResponse).get("id").asLong();

        stubLoadPrediction(1198.55);
        stubAnomalyDetection(true, 2.41, 0.72, "MEDIUM",
                "Updated record now looks unusual for this context.", "local_outlier_factor");

        mockMvc.perform(put("/api/load/update/" + predictionId)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", ts.toString(),
                                "temperature", 31.5,
                                "humidity", 77.0,
                                "publicEvent", 0
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("updated"))
                .andExpect(jsonPath("$.predicted_load").value(1198.55))
                .andExpect(jsonPath("$.is_anomaly").value(true))
                .andExpect(jsonPath("$.severity").value("MEDIUM"));

        LoadRequest updated = loadRepository.findById(predictionId).orElseThrow();
        assertEquals(31.5, updated.getTemperature(), 0.0001);
        assertEquals(77.0, updated.getHumidity(), 0.0001);
        assertEquals(0, updated.getPublicEvent());
        assertEquals(1198.55, updated.getPredictedLoad(), 0.0001);
        assertTrue(Boolean.TRUE.equals(updated.getIsAnomaly()));
        assertEquals(2.41, updated.getAnomalyScore(), 0.0001);
        assertEquals(1, anomalyRepository.count());
    }

    @Test
    void updateHistoryRow_ClearsOldFeedbackAndResolvedAnomalyFlags() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(2).withNano(0);
        stubLoadPrediction(1200.752);
        stubAnomalyDetection(true, 3.186, 0.8775, "HIGH",
                "Predicted load is strongly abnormal for this context.", "local_outlier_factor");
        stubAnomalyFeedbackAccepted();

        String forecastResponse = mockMvc.perform(post("/api/forecast")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", ts.toString(),
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
                                "message", "Looks correct",
                                "rating", 5
                        ))))
                .andExpect(status().isOk());

        assertEquals(1, feedbackRepository.count());
        assertEquals(1, anomalyRepository.count());

        stubLoadPrediction(1205.842);
        stubAnomalyDetection(false, 0.182, 0.58, "NORMAL",
                "Load behavior matches historical patterns.", "local_outlier_factor");

        mockMvc.perform(put("/api/load/update/" + predictionId)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", ts.toString(),
                                "temperature", 28.0,
                                "humidity", 80.0,
                                "publicEvent", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_anomaly").value(false))
                .andExpect(jsonPath("$.severity").value("NORMAL"));

        LoadRequest updated = loadRepository.findById(predictionId).orElseThrow();
        assertFalse(Boolean.TRUE.equals(updated.getFeedbackGiven()));
        assertNull(updated.getFeedbackAgreed());
        assertFalse(Boolean.TRUE.equals(updated.getIsAnomaly()));
        assertEquals(0, feedbackRepository.count());
        assertEquals(0, anomalyRepository.count());
    }
}
