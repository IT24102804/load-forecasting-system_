package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackStatus;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        mockMvc.perform(get("/api/admin/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(saved.getId()))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mockMvc.perform(put("/api/admin/feedback/" + saved.getId())
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
}
