package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AnomalyUiRegressionSystemFlowTest extends AbstractSystemFlowTest {

    @Test
    void anomalyPages_RenderPersistedConfidenceAndReasoningDetails() throws Exception {
        Anomaly anomaly = new Anomaly();
        anomaly.setPredictionId(60L);
        anomaly.setTimestamp(LocalDateTime.of(2026, 4, 13, 14, 0));
        anomaly.setLoadDemand(1200.752);
        anomaly.setTemperature(30.0);
        anomaly.setHumidity(80.0);
        anomaly.setHourOfDay(14);
        anomaly.setDayOfWeek(1);
        anomaly.setMonth(4);
        anomaly.setPublicEvent(0);
        anomaly.setAnomalyScore(3.186);
        anomaly.setConfidence(0.8775);
        anomaly.setSeverity("HIGH");
        anomaly.setReason("Predicted load of 1200.8 kW is strongly abnormal for 30.0°C at hour 14.");
        anomaly.setStatus("OPEN");
        Anomaly saved = anomalyRepository.save(anomaly);

        mockMvc.perform(get("/anomaly").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("anomaly/dashboard"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confidence")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("87.8%")));

        mockMvc.perform(get("/anomaly/list").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("anomaly/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confidence")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("87.8%")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Predicted load of 1200.8 kW is strongly abnormal")));

        mockMvc.perform(get("/anomaly/" + saved.getId()).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("anomaly/detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confidence")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("87.8 %")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Detection Reason")));
    }
}
