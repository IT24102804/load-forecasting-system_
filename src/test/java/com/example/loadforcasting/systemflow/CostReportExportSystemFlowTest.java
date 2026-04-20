package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.CostPredictionRun;
import com.example.loadforcasting.Entity.GenerationMixRun;
import com.example.loadforcasting.Repository.CostPredictionRunRepository;
import com.example.loadforcasting.Repository.GenerationMixRunRepository;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CostReportExportSystemFlowTest extends AbstractSystemFlowTest {

    @Autowired
    private GenerationMixRunRepository generationMixRunRepository;

    @Autowired
    private CostPredictionRunRepository costPredictionRunRepository;

    @Test
    void exportCostReportPdf_AfterCostPrediction_ReturnsPdfAttachmentBytes() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(1).withNano(0);
        stubLoadPrediction(1205.842);
        stubAnomalyDetection(false, 0.182, 0.58, "NORMAL",
                "Load behavior matches historical patterns.", "local_outlier_factor");
        stubGenerationMixPrediction(23660.0, 8200.0, 7100.0, 4200.0, 1450.0, 910.0, 1800.0);
        stubCostPrediction(52.0);

        mockMvc.perform(post("/api/forecast")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", ts.toString(),
                                "temperature", 28.0,
                                "humidity", 80.0,
                                "publicEvent", 1
                        ))))
                .andExpect(status().isOk());

        Long predictionId = firstLoadRequest().getId();

        mockMvc.perform(post("/api/generation-mix/predict")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "predictionId", predictionId,
                                "reservoirPct", 72.5
                        ))))
                .andExpect(status().isOk());

        GenerationMixRun mixRun = generationMixRunRepository.findAll().stream().findFirst().orElseThrow();

        mockMvc.perform(post("/api/cost/predict")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "runId", mixRun.getId(),
                                "foPrice", 1000.0,
                                "coalPrice", 2000.0,
                                "dieselPrice", 1500.0,
                                "naphthaPrice", 1600.0
                        ))))
                .andExpect(status().isOk());

        assertEquals(1, costPredictionRunRepository.count());
        CostPredictionRun costRun = costPredictionRunRepository.findAll().get(0);

        mockMvc.perform(get("/api/reports/full-chain/export")
                        .param("costRunId", String.valueOf(costRun.getId())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));

        byte[] pdf = mockMvc.perform(get("/api/reports/full-chain/export")
                        .param("costRunId", String.valueOf(costRun.getId())))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertTrue(new String(pdf, 0, Math.min(pdf.length, 8)).contains("%PDF"));
    }
}
