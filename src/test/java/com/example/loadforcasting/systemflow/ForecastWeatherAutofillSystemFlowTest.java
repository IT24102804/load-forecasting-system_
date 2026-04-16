package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ForecastWeatherAutofillSystemFlowTest extends AbstractSystemFlowTest {

    @Test
    void predictTransient_ReturnsWeatherWithoutPersistingHistory() throws Exception {
        stubWeatherPrediction(29.4, 78.1, 3.2, 1.5, 512.4);

        mockMvc.perform(post("/api/weather/predict-transient")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("timestamp", "2026-04-13T14:30:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature").value(29.4))
                .andExpect(jsonPath("$.humidity").value(78.1))
                .andExpect(jsonPath("$.source").value("python_model"));

        assertEquals(0, weatherPredictionRepository.count());
    }

    @Test
    void predictTransientThenForecast_UsesReturnedWeatherWithoutCreatingWeatherRows() throws Exception {
        stubWeatherPrediction(28.0, 80.0, 3.0, 0.0, 500.0);
        stubLoadPrediction(1205.842);
        stubAnomalyDetection(false, 0.182, 0.58, "NORMAL",
                "Load behavior matches historical patterns.", "local_outlier_factor");

        mockMvc.perform(post("/api/weather/predict-transient")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("timestamp", "2026-04-13T14:00:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature").value(28.0))
                .andExpect(jsonPath("$.humidity").value(80.0));

        mockMvc.perform(post("/api/forecast")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", "2026-04-13T14:00:00",
                                "temperature", 28.0,
                                "humidity", 80.0,
                                "publicEvent", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.load_demand").value(1205.842))
                .andExpect(jsonPath("$.is_anomaly").value(false));

        assertEquals(0, weatherPredictionRepository.count());
        assertEquals(1, loadRepository.count());
    }
}
