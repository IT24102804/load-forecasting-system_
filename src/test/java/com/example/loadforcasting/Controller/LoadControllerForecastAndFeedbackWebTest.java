package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Entity.LoadForecastRun;
import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.Repository.UserRepository;
import com.example.loadforcasting.Service.AnomalyDetectionService;
import com.example.loadforcasting.Service.FeedbackService;
import com.example.loadforcasting.Service.LoadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.hamcrest.Matchers.closeTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoadController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoadControllerForecastAndFeedbackWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoadService loadService;

    @MockBean
    private AnomalyDetectionService anomalyDetectionService;

    @MockBean
    private FeedbackService feedbackService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void forecast_ReturnsExpandedAnomalyPayload() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(1).withNano(0);
        when(loadService.predictAndSave(any(LoadRequest.class))).thenAnswer(invocation -> {
            LoadRequest request = invocation.getArgument(0);
            request.setId(43L);
            return 1199.294921875;
        });
        when(anomalyDetectionService.detectAnomaly(
                anyLong(), any(LocalDateTime.class), anyDouble(), anyDouble(), anyDouble(),
                any(Integer.class), any(Integer.class), any(Integer.class), any(Integer.class)
        )).thenReturn(Map.of(
                "is_anomaly", true,
                "anomaly_score", 1.9922964233164904,
                "confidence", 0.6847085559276253,
                "severity", "MEDIUM",
                "reason", "Unusual load pattern detected compared to normal Monday behavior.",
                "source", "python_model",
                "model_name", "local_outlier_factor"
        ));

        mockMvc.perform(post("/api/forecast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  \"timestamp\": \"%s\",
                                  \"temperature\": 29.0,
                                  \"humidity\": 79.0,
                                  \"public_event\": 0
                                }
                                """, ts)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(43))
                .andExpect(jsonPath("$.load_demand", closeTo(1199.2949, 0.0001)))
                .andExpect(jsonPath("$.is_anomaly").value(true))
                .andExpect(jsonPath("$.anomaly_score", closeTo(1.9922964, 0.0001)))
                .andExpect(jsonPath("$.confidence", closeTo(0.6847085, 0.0001)))
                .andExpect(jsonPath("$.severity").value("MEDIUM"))
                .andExpect(jsonPath("$.reason").value("Unusual load pattern detected compared to normal Monday behavior."))
                .andExpect(jsonPath("$.source").value("python_model"))
                .andExpect(jsonPath("$.model_name").value("local_outlier_factor"));

        verify(loadService).updateRequestWithAnomalyInfo(any(LoadRequest.class));
    }

    @Test
    void forecast_NormalPrediction_ReturnsNormalPayload() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(2).withNano(0);
        when(loadService.predictAndSave(any(LoadRequest.class))).thenAnswer(invocation -> {
            LoadRequest request = invocation.getArgument(0);
            request.setId(52L);
            return 1525.0;
        });
        when(anomalyDetectionService.detectAnomaly(
                anyLong(), any(LocalDateTime.class), anyDouble(), anyDouble(), anyDouble(),
                any(Integer.class), any(Integer.class), any(Integer.class), any(Integer.class)
        )).thenReturn(Map.of(
                "is_anomaly", false,
                "anomaly_score", 0.221,
                "confidence", 0.58,
                "severity", "NORMAL",
                "reason", "Load behavior matches historical patterns.",
                "source", "python_model",
                "model_name", "local_outlier_factor"
        ));

        mockMvc.perform(post("/api/forecast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  \"timestamp\": \"%s\",
                                  \"temperature\": 27.0,
                                  \"humidity\": 74.0,
                                  \"public_event\": 0
                                }
                                """, ts)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(52))
                .andExpect(jsonPath("$.load_demand", closeTo(1525.0, 0.0001)))
                .andExpect(jsonPath("$.is_anomaly").value(false))
                .andExpect(jsonPath("$.anomaly_score", closeTo(0.221, 0.0001)))
                .andExpect(jsonPath("$.confidence", closeTo(0.58, 0.0001)))
                .andExpect(jsonPath("$.severity").value("NORMAL"))
                .andExpect(jsonPath("$.reason").value("Load behavior matches historical patterns."))
                .andExpect(jsonPath("$.source").value("python_model"))
                .andExpect(jsonPath("$.model_name").value("local_outlier_factor"));

        verify(loadService).updateRequestWithAnomalyInfo(any(LoadRequest.class));
    }

    @Test
    void forecast_LoadPredictionFailure_ReturnsServerError() throws Exception {
        LocalDateTime ts = LocalDateTime.now(ZoneId.systemDefault()).plusHours(1).withNano(0);
        when(loadService.predictAndSave(any(LoadRequest.class)))
                .thenThrow(new RuntimeException("Load service offline"));

        mockMvc.perform(post("/api/forecast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  \"timestamp\": \"%s\",
                                  \"temperature\": 29.0,
                                  \"humidity\": 79.0,
                                  \"public_event\": 0
                                }
                                """, ts)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Prediction failed: Load service offline"));
    }

    @Test
    void feedback_StoresAgreementAndReturnsSuccess() throws Exception {
        LoadRequest storedRequest = new LoadRequest();
        storedRequest.setId(43L);
        storedRequest.setIsAnomaly(true);
        storedRequest.setAnomalyScore(1.9922964233164904);
        Feedback savedFeedback = new Feedback();
        savedFeedback.setId(8L);
        savedFeedback.setUserName("System User");
        savedFeedback.setMessage("Looks correct");
        savedFeedback.setFeedbackType(FeedbackType.ANOMALY_FEEDBACK);
        savedFeedback.setSubjectScope("ANOMALY");
        LoadForecastRun run = new LoadForecastRun();
        run.setId(17L);
        savedFeedback.setLoadForecastRun(run);
        Anomaly anomaly = new Anomaly();
        anomaly.setId(5L);
        savedFeedback.setAnomaly(anomaly);

        when(loadService.getRequestById(43L)).thenReturn(storedRequest);
        when(feedbackService.saveFeedback(any())).thenReturn(savedFeedback);

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prediction_id": 43,
                                  "user_agreed": true,
                                  "user_name": "System User",
                                  "message": "Looks correct",
                                  "rating": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("feedback completely integrated and saved"))
                .andExpect(jsonPath("$.feedbackId").value(8))
                .andExpect(jsonPath("$.predictionId").value(43))
                .andExpect(jsonPath("$.loadForecastRunId").value(17))
                .andExpect(jsonPath("$.anomalyId").value(5))
                .andExpect(jsonPath("$.feedbackType").value("ANOMALY_FEEDBACK"))
                .andExpect(jsonPath("$.subjectScope").value("ANOMALY"));

        verify(anomalyDetectionService).storeFeedback(43L, true, true, 1.9922964233164904);
        verify(feedbackService).saveFeedback(any());
        verify(loadService).updateRequestWithAnomalyInfo(any(LoadRequest.class));
    }

    @Test
    void feedback_MissingRequiredFields_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prediction_id": 43
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing prediction_id or user_agreed"));
    }
}
