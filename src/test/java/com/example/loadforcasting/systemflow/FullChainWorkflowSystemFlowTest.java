package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.CostPredictionRun;
import com.example.loadforcasting.Entity.GenerationMixRun;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Repository.CostPredictionRunRepository;
import com.example.loadforcasting.Repository.GenerationMixRunRepository;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FullChainWorkflowSystemFlowTest extends AbstractSystemFlowTest {

    @Autowired
    private GenerationMixRunRepository generationMixRunRepository;

    @Autowired
    private CostPredictionRunRepository costPredictionRunRepository;

    @Test
    void fullChainWorkflow_WeatherForecastAnomalyGenerationMixCostAndExport_AllSucceed() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(4).withNano(0);
        stubWeatherPrediction(29.4, 78.1, 3.2, 1.5, 512.4);
        stubLoadPrediction(1205.842);
        stubAnomalyDetection(false, 0.182, 0.58, "NORMAL",
                "Load behavior matches historical patterns.", "local_outlier_factor");
        stubGenerationMixPrediction(23660.0, 8200.0, 7100.0, 4200.0, 1450.0, 910.0, 1800.0);
        stubCostPrediction(52.0);

        mockMvc.perform(post("/api/weather/predict-transient")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("timestamp", ts.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature").value(29.4))
                .andExpect(jsonPath("$.humidity").value(78.1))
                .andExpect(jsonPath("$.wind_speed").value(3.2))
                .andExpect(jsonPath("$.rainfall").value(1.5))
                .andExpect(jsonPath("$.solar_irradiance").value(512.4))
                .andExpect(jsonPath("$.source").value("python_model"));

        MvcResult forecastResult = mockMvc.perform(post("/api/forecast")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", ts.toString(),
                                "temperature", 29.4,
                                "humidity", 78.1,
                                "publicEvent", 1,
                                "forceNew", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.load_demand").value(1205.842))
                .andExpect(jsonPath("$.is_anomaly").value(false))
                .andExpect(jsonPath("$.severity").value("NORMAL"))
                .andExpect(jsonPath("$.model_name").value("local_outlier_factor"))
                .andReturn();

        JsonNode forecastJson = objectMapper.readTree(forecastResult.getResponse().getContentAsString());
        long predictionId = forecastJson.get("id").asLong();

        mockMvc.perform(post("/api/generation-mix/predict")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "predictionId", predictionId,
                                "reservoirPct", 72.5,
                                "forceNew", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prediction_id").value(predictionId))
                .andExpect(jsonPath("$.forecast_load").value(1205.84))
                .andExpect(jsonPath("$.estimated_load_demand").value(28940.21))
                .andExpect(jsonPath("$.total_mwh").value(23660.0))
                .andExpect(jsonPath("$.prediction.major_hydro").value(8200.0));

        GenerationMixRun mixRun = generationMixRunRepository.findAll().stream().findFirst().orElseThrow();

        MvcResult costResult = mockMvc.perform(post("/api/cost/predict")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "runId", mixRun.getId(),
                                "foPrice", 1000.0,
                                "coalPrice", 2000.0,
                                "dieselPrice", 1500.0,
                                "naphthaPrice", 1600.0,
                                "forceNew", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit_cost").value(52.0))
                .andExpect(jsonPath("$.cost_run_id").exists())
                .andReturn();

        JsonNode costJson = objectMapper.readTree(costResult.getResponse().getContentAsString());
        long costRunId = costJson.get("cost_run_id").asLong();

        mockMvc.perform(get("/api/reports/full-chain/export")
                        .param("costRunId", String.valueOf(costRunId)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));

        byte[] pdf = mockMvc.perform(get("/api/reports/full-chain/export")
                        .param("costRunId", String.valueOf(costRunId)))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertEquals(0, weatherPredictionRepository.count());
        assertEquals(1, loadRepository.count());
        assertEquals(1, generationMixRunRepository.count());
        assertEquals(1, costPredictionRunRepository.count());
        assertTrue(lastGenerationMixRequestBody().contains("\"load_demand\":28940.21"));
        assertTrue(lastCostPredictionRequestBody().contains("\"fo_price\":1000.0"));
        assertTrue(new String(pdf, 0, Math.min(pdf.length, 8)).contains("%PDF"));

        LoadRequest savedForecast = firstLoadRequest();
        CostPredictionRun savedCostRun = costPredictionRunRepository.findById(costRunId).orElseThrow();
        assertEquals(predictionId, savedForecast.getId());
        assertEquals(mixRun.getId(), savedCostRun.getGenerationMixResultId());
    }
}
