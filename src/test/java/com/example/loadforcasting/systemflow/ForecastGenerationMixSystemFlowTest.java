package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ForecastGenerationMixSystemFlowTest extends AbstractSystemFlowTest {

    @Test
    void forecastThenGenerationMix_UsesSavedForecastDemandBridge() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(1).withNano(0);
        stubLoadPrediction(1205.842);
        stubAnomalyDetection(false, 0.182, 0.58, "NORMAL",
                "Load behavior matches historical patterns.", "local_outlier_factor");

        mockMvc.perform(post("/api/forecast")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", ts.toString(),
                                "temperature", 28.0,
                                "humidity", 80.0,
                                "publicEvent", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.load_demand").value(1205.842))
                .andExpect(jsonPath("$.is_anomaly").value(false));

        LoadRequest savedForecast = firstLoadRequest();
        stubGenerationMixPrediction(23660.0, 8200.0, 7100.0, 4200.0, 1450.0, 910.0, 1800.0);

        mockMvc.perform(post("/api/generation-mix/predict")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "predictionId", savedForecast.getId(),
                                "reservoirPct", 72.5
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prediction_id").value(savedForecast.getId()))
                .andExpect(jsonPath("$.forecast_load").value(1205.84))
                .andExpect(jsonPath("$.estimated_load_demand").value(28940.21))
                .andExpect(jsonPath("$.total_mwh").value(23660.0))
                .andExpect(jsonPath("$.prediction.major_hydro").value(8200.0));

        assertEquals(1, loadRepository.count());
        assertTrue(lastGenerationMixRequestBody().contains("\"date\":\"" + ts.toLocalDate() + "\""));
        assertTrue(lastGenerationMixRequestBody().contains("\"reservoir_pct\":72.5"));
        assertTrue(lastGenerationMixRequestBody().contains("\"load_demand\":28940.21"));
    }
}
