package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.Repository.UserRepository;
import com.example.loadforcasting.Service.FeedbackService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(FeedbackController.class)
@AutoConfigureMockMvc(addFilters = false)
class FeedbackControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FeedbackService feedbackService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void showSubmitForm_ReturnsSubmitViewWithTypes() throws Exception {
        mockMvc.perform(get("/feedback/submit"))
                .andExpect(status().isOk())
                .andExpect(view().name("feedback/submit"))
                .andExpect(model().attributeExists("feedback", "feedbackTypes"));
    }

    @Test
    void showSubmitForm_WithAnomalyContext_PrefillsModel() throws Exception {
        mockMvc.perform(get("/feedback/submit")
                        .param("predictionId", "43")
                        .param("anomalyId", "5")
                        .param("feedbackType", "ANOMALY_FEEDBACK"))
                .andExpect(status().isOk())
                .andExpect(view().name("feedback/submit"))
                .andExpect(model().attribute("anomalyContext", true))
                .andExpect(model().attribute("contextPredictionId", 43L))
                .andExpect(model().attribute("contextAnomalyId", 5L))
                .andExpect(model().attributeExists("feedback", "feedbackTypes"));
    }

    @Test
    void submitFeedback_InvalidEmail_RedirectsBackWithFlashError() throws Exception {
        mockMvc.perform(post("/feedback/submit")
                        .param("userName", "Test User")
                        .param("userEmail", "invalid-email")
                        .param("feedbackType", "GENERAL")
                        .param("message", "This is a test message"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feedback/submit"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(feedbackService, never()).saveFeedback(any());
    }

    @Test
    void submitFeedback_InvalidEmailWithAnomalyContext_RedirectsBackWithContext() throws Exception {
        mockMvc.perform(post("/feedback/submit")
                        .param("predictionId", "43")
                        .param("anomalyId", "5")
                        .param("userName", "Test User")
                        .param("userEmail", "invalid-email")
                        .param("message", "Needs review")
                        .param("feedbackType", "GENERAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feedback/submit?predictionId=43&feedbackType=ANOMALY_FEEDBACK&anomalyId=5"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(feedbackService, never()).saveFeedback(any());
    }

    @Test
    void submitFeedback_ValidEmail_SavesAndRedirectsToHistory() throws Exception {
        Feedback saved = new Feedback();
        saved.setId(12L);
        saved.setUserEmail("test@example.com");

        when(feedbackService.saveFeedback(any(Feedback.class))).thenReturn(saved);

        mockMvc.perform(post("/feedback/submit")
                        .param("userName", "Test User")
                        .param("userEmail", "test@example.com")
                        .param("feedbackType", "GENERAL")
                        .param("message", "This is a valid feedback message")
                        .param("rating", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feedback/my-feedback?email=test@example.com"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(feedbackService).saveFeedback(any(Feedback.class));
    }

    @Test
    void submitFeedback_WithPredictionContext_ForcesAnomalyFeedback() throws Exception {
        Feedback saved = new Feedback();
        saved.setId(13L);
        saved.setUserEmail("test@example.com");

        when(feedbackService.saveFeedback(any(Feedback.class))).thenReturn(saved);

        mockMvc.perform(post("/feedback/submit")
                        .param("predictionId", "43")
                        .param("userName", "Test User")
                        .param("userEmail", "test@example.com")
                        .param("feedbackType", "GENERAL")
                        .param("message", "Detailed anomaly note"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feedback/my-feedback?email=test@example.com"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(feedbackService).saveFeedback(argThat(feedback ->
                feedback.getPredictionId() != null
                        && feedback.getPredictionId().equals(43L)
                        && feedback.getFeedbackType() == FeedbackType.ANOMALY_FEEDBACK));
    }

    @Test
    void viewMyFeedback_WithEmail_PopulatesHistoryModel() throws Exception {
        Feedback feedback = new Feedback();
        feedback.setId(3L);
        feedback.setUserEmail("test@example.com");
        feedback.setFeedbackType(FeedbackType.GENERAL);

        when(feedbackService.getFeedbackByEmail("test@example.com")).thenReturn(List.of(feedback));

        mockMvc.perform(get("/feedback/my-feedback").param("email", "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("feedback/my-feedback"))
                .andExpect(model().attribute("email", "test@example.com"))
                .andExpect(model().attribute("hasFeedback", true))
                .andExpect(model().attribute("feedbacks", hasSize(1)));
    }

    @Test
    void showEditForm_WhenOwnedAndEditable_ReturnsEditView() throws Exception {
        Feedback feedback = new Feedback();
        feedback.setId(8L);
        feedback.setUserEmail("test@example.com");
        feedback.setFeedbackType(FeedbackType.GENERAL);

        when(feedbackService.getFeedbackById(8L)).thenReturn(Optional.of(feedback));
        when(feedbackService.isEditable(8L)).thenReturn(true);

        mockMvc.perform(get("/feedback/edit/8").param("email", "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("feedback/edit"))
                .andExpect(model().attributeExists("feedback", "feedbackTypes"))
                .andExpect(model().attribute("email", "test@example.com"));
    }

    @Test
    void updateFeedback_Success_RedirectsToHistoryWithFlashMessage() throws Exception {
        Feedback original = new Feedback();
        original.setId(5L);
        original.setUserEmail("test@example.com");

        when(feedbackService.getFeedbackById(5L)).thenReturn(Optional.of(original));
        when(feedbackService.isEditable(5L)).thenReturn(true);

        mockMvc.perform(post("/feedback/update")
                        .param("id", "5")
                        .param("email", "test@example.com")
                        .param("userName", "Updated User")
                        .param("userEmail", "test@example.com")
                        .param("feedbackType", "GENERAL")
                        .param("message", "Updated message")
                        .param("rating", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feedback/my-feedback?email=test@example.com"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(feedbackService).updateFeedback(any(Feedback.class));
    }

    @Test
    void deleteFeedback_NotOwned_RedirectsWithError() throws Exception {
        Feedback feedback = new Feedback();
        feedback.setId(21L);
        feedback.setUserEmail("someoneelse@example.com");

        when(feedbackService.getFeedbackById(21L)).thenReturn(Optional.of(feedback));

        mockMvc.perform(post("/feedback/delete/21").param("email", "test@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feedback/my-feedback?email=test@example.com"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(feedbackService, never()).deleteFeedback(eq(21L));
    }
}
