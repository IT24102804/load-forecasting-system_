package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Repository.UserRepository;
import com.example.loadforcasting.Service.FeedbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminFeedbackRestController.class)
@Import({AdminSecurityFilter.class, SecurityConfig.class})
class AdminFeedbackRestControllerSecurityWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FeedbackService feedbackService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void getAllFeedback_WithoutAdminSession_ReturnsJsonForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/feedback"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Admin session is required."));
    }

    @Test
    void getAllFeedback_WithNonAdminSession_ReturnsJsonForbidden() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userid", 5);
        session.setAttribute("role", "User");

        mockMvc.perform(get("/api/admin/feedback").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Admin session is required."));
    }

    @Test
    void getAllFeedback_WithAdminSession_AllowsRequest() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userid", 1);
        session.setAttribute("role", "Admin");

        when(feedbackService.getAllFeedback()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/admin/feedback").session(session))
                .andExpect(status().isOk());
    }
}
