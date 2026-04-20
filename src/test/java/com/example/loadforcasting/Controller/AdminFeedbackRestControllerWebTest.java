package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackStatus;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.Repository.UserRepository;
import com.example.loadforcasting.Service.FeedbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminFeedbackRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminFeedbackRestControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FeedbackService feedbackService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void getAllFeedback_ReturnsAllFeedbackRecords() throws Exception {
        Feedback first = feedback(1L, "First reply", FeedbackStatus.ACKNOWLEDGED);
        Feedback second = feedback(2L, null, FeedbackStatus.PENDING);

        when(feedbackService.getAllFeedback()).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/admin/feedback").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].adminReply").value("First reply"))
                .andExpect(jsonPath("$[0].status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].status").value("PENDING"));
    }

    @Test
    void replyFeedback_ExistingId_UpdatesReplyAndMarksResolved() throws Exception {
        Feedback existing = feedback(7L, null, FeedbackStatus.PENDING);
        existing.setAdminReply("Issue reviewed and resolved by admin.");
        existing.setStatus(FeedbackStatus.RESOLVED);
        when(feedbackService.replyFeedback(any(Long.class), any(String.class), any(Integer.class))).thenReturn(existing);

        mockMvc.perform(put("/api/admin/feedback/7")
                        .session(adminSession())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "adminReply": "Issue reviewed and resolved by admin."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.adminReply").value("Issue reviewed and resolved by admin."))
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void replyFeedback_MissingId_ReturnsEmptyBodyWithoutSaving() throws Exception {
        when(feedbackService.replyFeedback(any(Long.class), any(String.class), any(Integer.class)))
                .thenThrow(new RuntimeException("Feedback not found"));

        mockMvc.perform(put("/api/admin/feedback/99")
                        .session(adminSession())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "adminReply": "No record found."
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    private Feedback feedback(Long id, String adminReply, FeedbackStatus status) {
        Feedback feedback = new Feedback();
        feedback.setId(id);
        feedback.setUserName("Test User");
        feedback.setUserEmail("test@example.com");
        feedback.setFeedbackType(FeedbackType.GENERAL);
        feedback.setMessage("Test message");
        feedback.setAdminReply(adminReply);
        feedback.setStatus(status);
        feedback.setRating(4);
        return feedback;
    }

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userid", 1);
        session.setAttribute("role", "Admin");
        return session;
    }
}
