package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.GenerationMixRun;
import com.example.loadforcasting.Repository.CostPredictionRunRepository;
import com.example.loadforcasting.Repository.UserRepository;
import com.example.loadforcasting.Service.CostPredictionService;
import com.example.loadforcasting.Service.GenerationMixService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CostPredictionController.class)
@AutoConfigureMockMvc(addFilters = false)
class CostPredictionControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GenerationMixService generationMixService;

    @MockBean
    private CostPredictionService costPredictionService;

    @MockBean
    private CostPredictionRunRepository costPredictionRunRepository;

    @MockBean
    private UserRepository userRepository;

    @Test
    void predict_WithValidRequestAndFutureMixRun_ReturnsOk() throws Exception {
        GenerationMixRun mixRun = new GenerationMixRun();
        mixRun.setId(10L);
        mixRun.setForecastTimestamp(LocalDateTime.now().plusDays(1));
        mixRun.setEstimatedDailyDemandMwh(28940.21);

        when(generationMixService.getRunById(10L)).thenReturn(Optional.of(mixRun));
        when(costPredictionService.predictCost(eq(mixRun), eq(1000.0), eq(2000.0), eq(1500.0), eq(1600.0)))
                .thenReturn(Map.of("unit_cost", 52.0, "cost_run_id", 1L));

        mockMvc.perform(post("/api/cost/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runId": 10,
                                  "foPrice": 1000.0,
                                  "coalPrice": 2000.0,
                                  "dieselPrice": 1500.0,
                                  "naphthaPrice": 1600.0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit_cost").value(52.0))
                .andExpect(jsonPath("$.cost_run_id").value(1));

        verify(costPredictionService).predictCost(mixRun, 1000.0, 2000.0, 1500.0, 1600.0);
    }

    @Test
    void predict_WithPastMixRun_ReturnsOk() throws Exception {
        GenerationMixRun mixRun = new GenerationMixRun();
        mixRun.setId(10L);
        mixRun.setForecastTimestamp(LocalDate.now().minusDays(1).atStartOfDay());
        mixRun.setEstimatedDailyDemandMwh(28940.21);

        when(generationMixService.getRunById(10L)).thenReturn(Optional.of(mixRun));
        when(costPredictionService.predictCost(eq(mixRun), eq(1000.0), eq(2000.0), eq(1500.0), eq(1600.0)))
                .thenReturn(Map.of("unit_cost", 52.0, "cost_run_id", 1L));

        mockMvc.perform(post("/api/cost/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runId": 10,
                                  "foPrice": 1000.0,
                                  "coalPrice": 2000.0,
                                  "dieselPrice": 1500.0,
                                  "naphthaPrice": 1600.0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit_cost").value(52.0))
                .andExpect(jsonPath("$.cost_run_id").value(1));

        verify(costPredictionService).predictCost(mixRun, 1000.0, 2000.0, 1500.0, 1600.0);
    }

    @Test
    void predict_WithInvalidFuelPrice_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/cost/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runId": 10,
                                  "foPrice": -1.0,
                                  "coalPrice": 2000.0,
                                  "dieselPrice": 1500.0,
                                  "naphthaPrice": 1600.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Fuel prices must be valid numbers greater than 0 and not more than 2000."));

        verify(generationMixService, never()).getRunById(any());
    }

    @Test
    void update_WhenCostRunMissing_ReturnsNotFound() throws Exception {
        when(costPredictionRunRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/cost/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runId": 10,
                                  "foPrice": 1000.0,
                                  "coalPrice": 2000.0,
                                  "dieselPrice": 1500.0,
                                  "naphthaPrice": 1600.0
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Saved cost prediction run not found."));

        verify(generationMixService, never()).getRunById(any());
    }

    @Test
    void predict_WhenMixRunMissing_ReturnsNotFound() throws Exception {
        when(generationMixService.getRunById(10L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/cost/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runId": 10,
                                  "foPrice": 1000.0,
                                  "coalPrice": 2000.0,
                                  "dieselPrice": 1500.0,
                                  "naphthaPrice": 1600.0
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Saved generation mix run not found."));

        verify(costPredictionService, never()).predictCost(any(), any(Double.class), any(Double.class), any(Double.class), any(Double.class));
    }

    @Test
    void predict_WhenMixRunMissingTimestamp_ReturnsBadRequest() throws Exception {
        GenerationMixRun mixRun = new GenerationMixRun();
        mixRun.setId(10L);
        mixRun.setForecastTimestamp(null);

        when(generationMixService.getRunById(10L)).thenReturn(Optional.of(mixRun));

        mockMvc.perform(post("/api/cost/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runId": 10,
                                  "foPrice": 1000.0,
                                  "coalPrice": 2000.0,
                                  "dieselPrice": 1500.0,
                                  "naphthaPrice": 1600.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Saved generation mix run is missing forecast timestamp."));

        verify(costPredictionService, never()).predictCost(any(), any(Double.class), any(Double.class), any(Double.class), any(Double.class));
    }

    @Test
    void predict_WhenServiceThrowsIllegalArgument_ReturnsBadRequest() throws Exception {
        GenerationMixRun mixRun = new GenerationMixRun();
        mixRun.setId(10L);
        mixRun.setForecastTimestamp(LocalDateTime.now());

        when(generationMixService.getRunById(10L)).thenReturn(Optional.of(mixRun));
        when(costPredictionService.predictCost(eq(mixRun), eq(1000.0), eq(2000.0), eq(1500.0), eq(1600.0)))
                .thenThrow(new IllegalArgumentException("Bad fuel prices"));

        mockMvc.perform(post("/api/cost/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runId": 10,
                                  "foPrice": 1000.0,
                                  "coalPrice": 2000.0,
                                  "dieselPrice": 1500.0,
                                  "naphthaPrice": 1600.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad fuel prices"));
    }

    @Test
    void predict_WhenServiceThrowsRuntime_ReturnsBadGateway() throws Exception {
        GenerationMixRun mixRun = new GenerationMixRun();
        mixRun.setId(10L);
        mixRun.setForecastTimestamp(LocalDateTime.now());

        when(generationMixService.getRunById(10L)).thenReturn(Optional.of(mixRun));
        when(costPredictionService.predictCost(eq(mixRun), eq(1000.0), eq(2000.0), eq(1500.0), eq(1600.0)))
                .thenThrow(new RuntimeException("Service unavailable"));

        mockMvc.perform(post("/api/cost/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runId": 10,
                                  "foPrice": 1000.0,
                                  "coalPrice": 2000.0,
                                  "dieselPrice": 1500.0,
                                  "naphthaPrice": 1600.0
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Service unavailable"));
    }

    @Test
    void recentPoints_FiltersNullsAndMapsFields() throws Exception {
        var good = new com.example.loadforcasting.Entity.CostPredictionRun();
        good.setId(1L);
        good.setForecastTimestamp(LocalDateTime.of(2026, 4, 10, 10, 0));
        good.setUnitCost(52.0);
        var mixRun = new com.example.loadforcasting.Entity.GenerationMixRun();
        mixRun.setId(99L);
        good.setGenerationMixRun(mixRun);

        var missingUnitCost = new com.example.loadforcasting.Entity.CostPredictionRun();
        missingUnitCost.setId(2L);
        missingUnitCost.setForecastTimestamp(LocalDateTime.of(2026, 4, 10, 10, 0));
        missingUnitCost.setUnitCost(null);

        when(costPredictionRunRepository.findTop200ByOrderByForecastTimestampAsc())
                .thenReturn(Arrays.asList(good, null, missingUnitCost));

        mockMvc.perform(get("/api/cost/recent/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timestamp").value("2026-04-10T10:00"))
                .andExpect(jsonPath("$[0].unitCost").value(52.0))
                .andExpect(jsonPath("$[0].costRunId").value(1))
                .andExpect(jsonPath("$[0].setupId").value(99))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void history_WhenToBeforeFrom_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/cost/history")
                        .param("from", "2026-04-10")
                        .param("to", "2026-04-09"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("To date must be on or after From date."));
    }

    @Test
    void delete_WhenServiceThrowsIllegalArgument_ReturnsNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Saved cost prediction run not found."))
                .when(costPredictionService).deleteCostRun(99L);

        mockMvc.perform(delete("/api/cost/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Saved cost prediction run not found."));
    }
}
