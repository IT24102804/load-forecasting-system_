package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Repository.LoadRepository;
import com.example.loadforcasting.Repository.UserRepository;
import com.example.loadforcasting.Service.AnomalyDetectionService;
import com.example.loadforcasting.Service.FeedbackService;
import com.example.loadforcasting.Service.LoadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoadForecastRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoadForecastRestControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoadService loadService;

    @MockBean
    private LoadRepository loadRepository;

    @MockBean
    private AnomalyDetectionService anomalyDetectionService;

    @MockBean
    private FeedbackService feedbackService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void history_ReturnsSavedPredictionRows() throws Exception {
        LoadRequest request = new LoadRequest();
        request.setId(11L);
        request.setTimestamp(LocalDateTime.of(2026, 4, 13, 14, 0));
        request.setTemperature(28.0);
        request.setHumidity(80.0);
        request.setPublicEvent(1);
        request.setPredictedLoad(1205.842);

        when(loadRepository.findTop23ByOrderByIdDesc()).thenReturn(List.of(request));

        mockMvc.perform(get("/api/load/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].timestamp").value("2026-04-13T14:00"))
                .andExpect(jsonPath("$[0].public_event").value(1))
                .andExpect(jsonPath("$[0].predicted_load").value(1205.842));
    }

    @Test
    void updateRecord_Success_RecalculatesAndPersistsEditedPrediction() throws Exception {
        LoadRequest existing = new LoadRequest();
        existing.setId(15L);
        existing.setTimestamp(LocalDateTime.of(2026, 4, 13, 14, 0));
        existing.setTemperature(28.0);
        existing.setHumidity(80.0);
        existing.setPublicEvent(1);
        existing.setPredictedLoad(1205.842);

        when(loadRepository.findById(15L)).thenReturn(Optional.of(existing));
        when(loadService.predictValue(any(LoadRequest.class))).thenReturn(1198.55);
        when(anomalyDetectionService.detectAnomaly(
                anyLong(), any(LocalDateTime.class), anyDouble(), anyDouble(), anyDouble(),
                anyInt(), anyInt(), anyInt(), anyInt()
        )).thenReturn(Map.of(
                "is_anomaly", true,
                "anomaly_score", 2.41,
                "severity", "MEDIUM"
        ));

        mockMvc.perform(put("/api/load/update/15")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timestamp": "2026-04-13T14:00:00",
                                  "temperature": 31.5,
                                  "humidity": 77.0,
                                  "publicEvent": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("updated"))
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.predicted_load").value(1198.55))
                .andExpect(jsonPath("$.is_anomaly").value(true))
                .andExpect(jsonPath("$.severity").value("MEDIUM"))
                .andExpect(jsonPath("$.public_event").value(0));

        verify(loadService).predictValue(argThat(request ->
                request.getId().equals(15L)
                        && request.getTimestamp().equals(LocalDateTime.of(2026, 4, 13, 14, 0))
                        && request.getTemperature() == 31.5
                        && request.getHumidity() == 77.0
                        && request.getPublicEvent() == 0
        ));
        verify(anomalyDetectionService).clearAnomaliesForPrediction(15L);
        verify(feedbackService).deleteByPredictionId(15L);
        verify(loadService).updateRequestWithAnomalyInfo(argThat(request ->
                request.getPredictedLoad() == 1198.55
                        && Boolean.TRUE.equals(request.getIsAnomaly())
                        && request.getAnomalyScore() == 2.41
        ));
    }

    @Test
    void updateRecord_MissingPrediction_ReturnsNotFound() throws Exception {
        when(loadRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/load/update/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timestamp": "2026-04-13T14:00:00",
                                  "temperature": 31.5,
                                  "humidity": 77.0,
                                  "publicEvent": 0
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Prediction not found."));

        verify(loadService, never()).predictValue(any());
    }

    @Test
    void updateRecord_InvalidPayload_ReturnsBadRequest() throws Exception {
        LoadRequest existing = new LoadRequest();
        existing.setId(15L);
        when(loadRepository.findById(15L)).thenReturn(Optional.of(existing));

        mockMvc.perform(put("/api/load/update/15")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timestamp": "2026-04-13",
                                  "temperature": 31.5,
                                  "humidity": 77.0,
                                  "publicEvent": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Timestamp format is invalid."));

        verify(loadService, never()).predictValue(any());
    }
}
