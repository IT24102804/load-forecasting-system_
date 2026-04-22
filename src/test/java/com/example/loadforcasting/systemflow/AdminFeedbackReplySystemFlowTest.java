package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackStatus;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.Entity.LoadForecastRun;
import com.fasterxml.jackson.databind.JsonNode;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminFeedbackReplySystemFlowTest extends AbstractSystemFlowTest {

    @Test
    void adminFeedbackReplyFlow_ListsFeedbackAndPersistsReply() throws Exception {
        Feedback feedback = new Feedback();
        feedback.setUserName("System Tester");
        feedback.setUserEmail("tester@example.com");
        feedback.setFeedbackType(FeedbackType.GENERAL);
        feedback.setMessage("Please investigate this anomaly.");
        feedback.setStatus(FeedbackStatus.PENDING);
        feedback.setCreatedAt(LocalDateTime.of(2026, 4, 13, 10, 0));
        Feedback saved = feedbackRepository.save(feedback);

        mockMvc.perform(get("/api/admin/feedback").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(saved.getId()))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mockMvc.perform(put("/api/admin/feedback/" + saved.getId())
                        .session(adminSession())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "adminReply": "Reviewed by admin and resolved."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.adminReply").value("Reviewed by admin and resolved."))
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        Feedback updated = feedbackRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Reviewed by admin and resolved.", updated.getAdminReply());
        assertEquals(FeedbackStatus.RESOLVED, updated.getStatus());
    }

    @Test
    void adminFeedbackQueue_ReturnsDtoForFeedbackWithLinkedOperationalContext() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(1).withNano(0);
        String tsStr = ts.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        stubLoadPrediction(1450.0);

        String forecastResponse = mockMvc.perform(post("/api/forecast")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "timestamp", tsStr,
                                "temperature", 29.0,
                                "humidity", 74.0,
                                "publicEvent", 0
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long predictionId = objectMapper.readTree(forecastResponse).get("id").asLong();
        LoadForecastRun run = loadForecastRunRepository.findById(loadRepository.findById(predictionId).orElseThrow().getLoadForecastRunId())
                .orElseThrow();

        Feedback feedback = new Feedback();
        feedback.setUserName("Queue Tester");
        feedback.setUserEmail("queue@example.com");
        feedback.setFeedbackType(FeedbackType.ANOMALY_FEEDBACK);
        feedback.setMessage("Operationally linked feedback item.");
        feedback.setStatus(FeedbackStatus.PENDING);
        feedback.setLoadForecastRun(run);
        feedback.setPredictionId(predictionId);
        Feedback saved = feedbackRepository.save(feedback);

        String payload = mockMvc.perform(get("/api/admin/feedback").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(saved.getId()))
                .andExpect(jsonPath("$[0].userName").value("Queue Tester"))
                .andExpect(jsonPath("$[0].userEmail").value("queue@example.com"))
                .andExpect(jsonPath("$[0].message").value("Operationally linked feedback item."))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode first = objectMapper.readTree(payload).get(0);
        assertNotNull(first);
        assertEquals(6, first.size());
    }
}
