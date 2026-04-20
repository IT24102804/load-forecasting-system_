package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AdminAnomalyLifecycleSystemFlowTest extends AbstractSystemFlowTest {

    @Test
    void adminAnomalyLifecycle_DashboardListDetailAcknowledgeResolveUseRealPersistence() throws Exception {
        Anomaly anomaly = new Anomaly();
        anomaly.setPredictionId(42L);
        anomaly.setTimestamp(LocalDateTime.of(2026, 4, 13, 14, 0));
        anomaly.setLoadDemand(1200.752);
        anomaly.setTemperature(30.0);
        anomaly.setHumidity(80.0);
        anomaly.setHourOfDay(14);
        anomaly.setDayOfWeek(1);
        anomaly.setMonth(4);
        anomaly.setAnomalyScore(3.186);
        anomaly.setConfidence(0.8775);
        anomaly.setSeverity("HIGH");
        anomaly.setReason("Predicted load is strongly abnormal.");
        anomaly.setStatus("OPEN");
        Anomaly saved = anomalyRepository.save(anomaly);

        mockMvc.perform(get("/anomaly").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("anomaly/dashboard"))
                .andExpect(model().attribute("totalAnomalies", 1L))
                .andExpect(model().attribute("highCount", 1L));

        mockMvc.perform(get("/anomaly/list").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("anomaly/list"))
                .andExpect(model().attribute("totalCount", 1));

        mockMvc.perform(get("/anomaly/" + saved.getId()).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("anomaly/detail"))
                .andExpect(model().attributeExists("anomaly"))
                .andExpect(model().attribute("anomaly", org.hamcrest.Matchers.hasProperty("confidence", org.hamcrest.Matchers.closeTo(0.8775, 0.0001))));

        mockMvc.perform(post("/anomaly/" + saved.getId() + "/acknowledge").session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/anomaly/" + saved.getId()));

        Anomaly acknowledged = anomalyRepository.findById(saved.getId()).orElseThrow();
        assertEquals("ACKNOWLEDGED", acknowledged.getStatus());

        mockMvc.perform(post("/anomaly/" + saved.getId() + "/resolve")
                        .session(adminSession())
                        .param("resolutionNote", "Reviewed and resolved in system-flow test."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/anomaly/" + saved.getId()));

        Anomaly resolved = anomalyRepository.findById(saved.getId()).orElseThrow();
        assertEquals("RESOLVED", resolved.getStatus());
        assertEquals("Reviewed and resolved in system-flow test.", resolved.getResolutionNote());
        assertNotNull(resolved.getResolvedAt());
    }
}
