package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Service.AnomalyDetectionService;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ForecastAnomalyFallbackSystemFlowTest extends AbstractSystemFlowTest {

    @Autowired
    private AnomalyDetectionService anomalyDetectionService;

    @Test
    void forecast_WhenAnomalyServiceIsOffline_UsesRuleBasedFallbackAndPersistsAnomaly() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(3).withNano(0);
        stubLoadPrediction(7000.0);
        String originalUrl = (String) ReflectionTestUtils.getField(anomalyDetectionService, "anomalyServiceUrl");

        try {
            ReflectionTestUtils.setField(anomalyDetectionService, "anomalyServiceUrl", "http://localhost:1");

            mockMvc.perform(post("/api/forecast")
                            .contentType(APPLICATION_JSON)
                            .content(json(Map.of(
                                    "timestamp", ts.toString(),
                                    "temperature", 30.0,
                                    "humidity", 80.0,
                                    "publicEvent", 0
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.load_demand").value(7000.0))
                    .andExpect(jsonPath("$.is_anomaly").value(true))
                    .andExpect(jsonPath("$.model_name").value("hour_zscore_fallback"))
                    .andExpect(jsonPath("$.severity").value("HIGH"))
                    .andExpect(jsonPath("$.reason").isNotEmpty());

            assertEquals(1, loadRepository.count());
            assertEquals(1, anomalyRepository.count());

            LoadRequest savedRequest = firstLoadRequest();
            Anomaly savedAnomaly = anomalyRepository.findAll().get(0);

            assertTrue(Boolean.TRUE.equals(savedRequest.getIsAnomaly()));
            assertNotNull(savedRequest.getAnomalyScore());
            assertEquals("rule_based", savedAnomaly.getSource());
            assertEquals("hour_zscore_fallback", savedAnomaly.getModelName());
            assertEquals("HIGH", savedAnomaly.getSeverity());
            assertTrue(savedAnomaly.getAnomalyScore() > 0.9);
        } finally {
            ReflectionTestUtils.setField(anomalyDetectionService, "anomalyServiceUrl", originalUrl);
        }
    }
}
