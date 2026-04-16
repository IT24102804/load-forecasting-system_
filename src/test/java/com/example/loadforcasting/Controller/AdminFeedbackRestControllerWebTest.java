package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackStatus;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.Repository.FeedbackRepository;
import com.example.loadforcasting.Repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminFeedbackRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminFeedbackRestControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FeedbackRepository feedbackRepository;

    @MockBean
    private UserRepository userRepository;

    @Test
    void getAllFeedback_ReturnsAllFeedbackRecords() throws Exception {
        Feedback first = feedback(1L, "First reply", FeedbackStatus.ACKNOWLEDGED);
        Feedback second = feedback(2L, null, FeedbackStatus.PENDING);

        when(feedbackRepository.findAll()).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/admin/feedback"))
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
        when(feedbackRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/admin/feedback/7")
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

        verify(feedbackRepository).save(argThat(saved ->
                saved.getId().equals(7L)
                        && "Issue reviewed and resolved by admin.".equals(saved.getAdminReply())
                        && saved.getStatus() == FeedbackStatus.RESOLVED));
    }

    @Test
    void replyFeedback_MissingId_ReturnsEmptyBodyWithoutSaving() throws Exception {
        when(feedbackRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/admin/feedback/99")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "adminReply": "No record found."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(feedbackRepository, never()).save(any(Feedback.class));
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
}
