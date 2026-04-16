package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.WeatherPrediction;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class WeatherPredictionSystemFlowTest extends AbstractSystemFlowTest {

    @Test
    void weatherPredictionLifecycle_PredictsUpdatesAndDeletesWithRealPersistence() throws Exception {
        stubWeatherPrediction(29.4, 78.1, 3.2, 0.4, 610.0);

        mockMvc.perform(post("/predict")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("dateTime", "2026-04-13T14:30"))
                .andExpect(status().isOk())
                .andExpect(view().name("weather/weather"))
                .andExpect(model().attribute("success", true))
                .andExpect(model().attributeExists("prediction"));

        assertEquals(1, weatherPredictionRepository.count());
        WeatherPrediction saved = weatherPredictionRepository.findAll().get(0);
        assertNotNull(saved.getId());
        assertEquals(LocalDate.of(2026, 4, 13), saved.getPredictionDate());
        assertEquals(LocalTime.of(14, 30), saved.getPredictionTime());
        assertEquals(29.4, saved.getTemperature(), 0.0001);

        stubWeatherPrediction(31.1, 74.5, 4.1, 1.2, 580.0);

        mockMvc.perform(put("/api/predictions/" + saved.getId())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "dateTime": "2026-04-13T15:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.prediction.temperature").value(31.1))
                .andExpect(jsonPath("$.statistics.total").value(1));

        WeatherPrediction updated = weatherPredictionRepository.findById(saved.getId()).orElseThrow();
        assertEquals(LocalTime.of(15, 0), updated.getPredictionTime());
        assertEquals(31.1, updated.getTemperature(), 0.0001);

        mockMvc.perform(delete("/api/predictions/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.history").isEmpty())
                .andExpect(jsonPath("$.statistics.total").value(0));

        assertEquals(0, weatherPredictionRepository.count());
    }
}
