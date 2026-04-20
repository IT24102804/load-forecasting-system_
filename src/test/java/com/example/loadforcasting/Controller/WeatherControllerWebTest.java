package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.WeatherPrediction;
import com.example.loadforcasting.Repository.UserRepository;
import com.example.loadforcasting.Service.WeatherPredictionService;
import com.example.loadforcasting.dto.WeatherPredictionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(WeatherController.class)
@AutoConfigureMockMvc(addFilters = false)
class WeatherControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WeatherPredictionService weatherPredictionService;

    @MockBean
    private UserRepository userRepository;

    private Map<String, Object> weatherStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 0L);
        stats.put("avgTemperature", 28.4);
        stats.put("avgHumidity", 77.2);
        stats.put("maxTemperature", 33.0);
        stats.put("minTemperature", 24.5);
        stats.put("totalRainfall", 12.0);
        stats.put("monthlyTemps", new ArrayList<>());
        return stats;
    }

    @Test
    void home_ReturnsWeatherDashboardWithModelData() throws Exception {
        when(weatherPredictionService.getAllPredictions()).thenReturn(List.of());
        when(weatherPredictionService.getStatistics()).thenReturn(weatherStatistics());

        mockMvc.perform(get("/weather"))
                .andExpect(status().isOk())
                .andExpect(view().name("weather/weather"))
                .andExpect(model().attributeExists("predictionRequest", "history", "statistics"));
    }

    @Test
    void predict_ValidRequest_ReturnsPredictionAndSuccessMessage() throws Exception {
        WeatherPrediction prediction = new WeatherPrediction();
        prediction.setId(5L);
        prediction.setPredictionDate(LocalDate.of(2026, 4, 13));
        prediction.setPredictionTime(LocalTime.of(14, 30));
        prediction.setTemperature(29.4);
        prediction.setHumidity(78.1);

        when(weatherPredictionService.predictAndSave("2026-04-13T14:30")).thenReturn(prediction);
        when(weatherPredictionService.getAllPredictions()).thenReturn(List.of(prediction));
        Map<String, Object> stats = weatherStatistics();
        stats.put("total", 1L);
        when(weatherPredictionService.getStatistics()).thenReturn(stats);

        mockMvc.perform(post("/predict")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("dateTime", "2026-04-13T14:30"))
                .andExpect(status().isOk())
                .andExpect(view().name("weather/weather"))
                .andExpect(model().attribute("success", true))
                .andExpect(model().attributeExists("prediction"))
                .andExpect(model().attribute("history", hasSize(1)));

        verify(weatherPredictionService).predictAndSave("2026-04-13T14:30");
    }

    @Test
    void predict_InvalidYear_ReturnsValidationMessageWithoutCallingService() throws Exception {
        when(weatherPredictionService.getAllPredictions()).thenReturn(List.of());
        when(weatherPredictionService.getStatistics()).thenReturn(weatherStatistics());

        mockMvc.perform(post("/predict")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("dateTime", "2031-04-13T14:30"))
                .andExpect(status().isOk())
                .andExpect(view().name("weather/weather"))
                .andExpect(model().attribute("success", false))
                .andExpect(model().attributeExists("message"));

        verify(weatherPredictionService, never()).predictAndSave("2031-04-13T14:30");
    }

    @Test
    void predictTransient_ValidTimestamp_ReturnsWeatherJson() throws Exception {
        when(weatherPredictionService.predictTransient("2026-04-13T14:30:00"))
                .thenReturn(new WeatherPredictionResult(
                        LocalDateTime.of(2026, 4, 13, 14, 30),
                        29.4,
                        78.1,
                        3.2,
                        1.5,
                        512.4,
                        "python_model"
                ));

        mockMvc.perform(post("/api/weather/predict-transient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timestamp": "2026-04-13T14:30:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature").value(29.4))
                .andExpect(jsonPath("$.humidity").value(78.1))
                .andExpect(jsonPath("$.wind_speed").value(3.2))
                .andExpect(jsonPath("$.rainfall").value(1.5))
                .andExpect(jsonPath("$.solar_irradiance").value(512.4))
                .andExpect(jsonPath("$.source").value("python_model"));

        verify(weatherPredictionService).predictTransient("2026-04-13T14:30:00");
    }

    @Test
    void predictTransient_MissingTimestamp_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/weather/predict-transient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Timestamp is required"));

        verify(weatherPredictionService, never()).predictTransient(anyString());
    }

    @Test
    void predictTransient_InvalidTimestamp_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/weather/predict-transient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timestamp": "2026-04-13"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid timestamp format. Use YYYY-MM-DDTHH:MM or YYYY-MM-DDTHH:MM:SS"));

        verify(weatherPredictionService, never()).predictTransient(anyString());
    }

    @Test
    void predictTransient_FallbackResponse_ReturnsValidJson() throws Exception {
        when(weatherPredictionService.predictTransient("2026-04-13T14:30:00"))
                .thenReturn(new WeatherPredictionResult(
                        LocalDateTime.of(2026, 4, 13, 14, 30),
                        27.6,
                        73.0,
                        2.1,
                        0.0,
                        480.0,
                        "fallback"
                ));

        mockMvc.perform(post("/api/weather/predict-transient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timestamp": "2026-04-13T14:30:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("fallback"))
                .andExpect(jsonPath("$.temperature").value(27.6));
    }

    @Test
    void deletePrediction_Success_ReturnsUpdatedPayload() throws Exception {
        when(weatherPredictionService.getAllPredictions()).thenReturn(List.of());
        when(weatherPredictionService.getStatistics()).thenReturn(weatherStatistics());

        mockMvc.perform(delete("/api/predictions/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Prediction deleted successfully!"))
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.statistics.total").value(0))
                .andExpect(jsonPath("$.statistics.avgTemperature").value(28.4));

        verify(weatherPredictionService).deletePrediction(7L);
    }

    @Test
    void updatePrediction_ServiceFailure_ReturnsBadRequestPayload() throws Exception {
        when(weatherPredictionService.updatePrediction(eq(9L), eq("2026-04-13T12:00")))
                .thenThrow(new RuntimeException("Prediction with ID 9 not found"));

        mockMvc.perform(put("/api/predictions/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dateTime": "2026-04-13T12:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Prediction with ID 9 not found"));

        verify(weatherPredictionService).updatePrediction(9L, "2026-04-13T12:00");
    }
}
