package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ForecastAnomalySystemFlowTest extends AbstractSystemFlowTest {

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Test
    void forecast_NormalFlow_PersistsPredictionWithoutAnomalyRecord() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(1).withNano(0);
        stubLoadPrediction(1205.842);
        stubAnomalyDetection(false, 0.182, 0.58, "NORMAL",
                "Load behavior matches historical patterns.", "local_outlier_factor");

        mockMvc.perform(post("/api/forecast")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", ts.format(TS_FORMATTER),
                                "temperature", 28.0,
                                "humidity", 80.0,
                                "publicEvent", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.load_demand").value(1205.842))
                .andExpect(jsonPath("$.is_anomaly").value(false))
                .andExpect(jsonPath("$.severity").value("NORMAL"))
                .andExpect(jsonPath("$.model_name").value("local_outlier_factor"));

        assertEquals(1, loadRepository.count());
        assertEquals(0, anomalyRepository.count());

        LoadRequest saved = firstLoadRequest();
        assertNotNull(saved.getId());
        assertEquals(ts, saved.getTimestamp());
        assertEquals(1205.842, saved.getPredictedLoad(), 0.0001);
        assertFalse(Boolean.TRUE.equals(saved.getIsAnomaly()));
        assertEquals(0.182, saved.getAnomalyScore(), 0.0001);
    }

    @Test
    void forecast_AnomalyFlow_PersistsPredictionAndAnomalyRecord() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(2).withNano(0);
        stubLoadPrediction(1200.752);
        stubAnomalyDetection(true, 3.186, 0.8775, "HIGH",
                "Predicted load of 1200.8 kW is strongly abnormal for 30.0°C at hour 14.",
                "local_outlier_factor");

        mockMvc.perform(post("/api/forecast")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", ts.format(TS_FORMATTER),
                                "temperature", 30.0,
                                "humidity", 80.0,
                                "publicEvent", 0
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.load_demand").value(1200.752))
                .andExpect(jsonPath("$.is_anomaly").value(true))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.anomaly_score").value(3.186));

        assertEquals(1, loadRepository.count());
        assertEquals(1, anomalyRepository.count());

        LoadRequest savedRequest = firstLoadRequest();
        Anomaly savedAnomaly = anomalyRepository.findAll().get(0);

        assertNotNull(savedRequest.getId());
        assertTrue(Boolean.TRUE.equals(savedRequest.getIsAnomaly()));
        assertEquals(3.186, savedRequest.getAnomalyScore(), 0.0001);
        assertEquals(savedRequest.getId(), savedAnomaly.getPredictionId());
        assertEquals("HIGH", savedAnomaly.getSeverity());
        assertEquals("OPEN", savedAnomaly.getStatus());
        assertEquals(1200.752, savedAnomaly.getLoadDemand(), 0.0001);
    }
}
