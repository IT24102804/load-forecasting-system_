package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Repository.UserRepository;
import com.example.loadforcasting.Service.GenerationMixService;
import com.example.loadforcasting.Service.LoadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.hamcrest.Matchers.closeTo;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(GenerationMixController.class)
@AutoConfigureMockMvc(addFilters = false)
class GenerationMixControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoadService loadService;

    @MockBean
    private GenerationMixService generationMixService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void generationMixPage_WithPredictionId_LoadsForecastSummaryState() throws Exception {
        LoadRequest request = new LoadRequest();
        request.setId(60L);
        request.setTimestamp(LocalDateTime.now(ZoneId.systemDefault()).plusDays(1).withNano(0));
        request.setPredictedLoad(1205.842041015625);

        when(loadService.getRequestById(60L)).thenReturn(request);
        when(generationMixService.getEstimatedDailyDemandForView(request)).thenReturn(28940.21);

        mockMvc.perform(get("/generation-mix")
                        .param("predictionId", "60")
                        .session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("generationmix/index"))
                .andExpect(model().attributeExists("selectedPrediction", "estimatedDailyDemand"))
                .andExpect(model().attribute("estimatedDailyDemand", closeTo(28940.21, 0.0001)));
    }

    @Test
    void generationMixPage_WithoutPredictionId_LoadsInstructionState() throws Exception {
        mockMvc.perform(get("/generation-mix")
                        .session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("generationmix/index"))
                .andExpect(model().attributeExists("pageInstruction"));
    }

    @Test
    void generationMixPage_WithoutSession_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/generation-mix"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void predictGenerationMix_WithSavedForecast_ReturnsMixPayload() throws Exception {
        LoadRequest request = new LoadRequest();
        request.setId(60L);
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusDays(1).withNano(0);
        request.setTimestamp(ts);
        request.setPredictedLoad(1205.842041015625);

        when(loadService.getRequestById(60L)).thenReturn(request);
        when(generationMixService.predictGenerationMix(eq(request), eq(72.5))).thenReturn(Map.of(
                "prediction_id", 60L,
                "forecast_load", 1205.84,
                "estimated_load_demand", 28940.21,
                "total_mwh", 23660.0,
                "date", ts.toLocalDate().toString(),
                "prediction", Map.of("major_hydro", 8200.0),
                "percentages", Map.of("major_hydro", 34.6)
        ));

        mockMvc.perform(post("/api/generation-mix/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "predictionId": 60,
                                  "reservoirPct": 72.5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prediction_id").value(60))
                .andExpect(jsonPath("$.forecast_load", closeTo(1205.84, 0.0001)))
                .andExpect(jsonPath("$.estimated_load_demand", closeTo(28940.21, 0.0001)))
                .andExpect(jsonPath("$.total_mwh", closeTo(23660.0, 0.0001)));

        verify(generationMixService).predictGenerationMix(request, 72.5);
    }

    @Test
    void predictGenerationMix_InvalidReservoirValue_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/generation-mix/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "predictionId": 60,
                                  "reservoirPct": 125
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Reservoir percentage must be between 0 and 100."));

        verify(loadService, never()).getRequestById(60L);
        verify(generationMixService, never()).predictGenerationMix(org.mockito.ArgumentMatchers.any(), anyDouble());
    }

    @Test
    void predictGenerationMix_UnknownPrediction_ReturnsNotFound() throws Exception {
        when(loadService.getRequestById(99L)).thenReturn(null);

        mockMvc.perform(post("/api/generation-mix/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "predictionId": 99,
                                  "reservoirPct": 72.5
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Prediction not found for id 99."));
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userid", 1);
        session.setAttribute("role", "User");
        return session;
    }
}
