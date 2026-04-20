package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.Entity.LoadData;
import com.example.loadforcasting.Entity.ModelVersion;
import com.example.loadforcasting.Repository.AnomalyRepository;
import com.example.loadforcasting.Repository.AnomalyStatusEventRepository;
import com.example.loadforcasting.Repository.LoadDataRepository;
import com.example.loadforcasting.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceIntegrationTest {

    @Mock
    private AnomalyRepository anomalyRepository;

    @Mock
    private LoadDataRepository loadDataRepository;

    @Mock
    private AnomalyStatusEventRepository anomalyStatusEventRepository;

    @Mock
    private LoadService loadService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ModelVersionService modelVersionService;

    @InjectMocks
    private AnomalyDetectionService anomalyService;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(anomalyService, "anomalyServiceUrl", "http://localhost:5002");
        ModelVersion modelVersion = new ModelVersion();
        modelVersion.setId(1L);
        modelVersion.setVersionLabel("v1");
        modelVersion.setModelName("local_outlier_factor");
        lenient().when(modelVersionService.resolveCurrent(anyString(), anyString())).thenReturn(modelVersion);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(anomalyService, "restTemplate");
        assertNotNull(restTemplate);
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void detectAnomaly_PythonResponse_MapsFieldsAndSavesAnomaly() {
        LocalDateTime forecastTimestamp = LocalDateTime.of(2026, 4, 13, 14, 0);

        mockServer.expect(requestTo("http://localhost:5002/detect_anomaly"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.load").value(1948.0))
                .andExpect(jsonPath("$.temp").value(29.0))
                .andExpect(jsonPath("$.humidity").value(79.0))
                .andExpect(jsonPath("$.hour").value(14))
                .andExpect(jsonPath("$.day").value(1))
                .andExpect(jsonPath("$.month").value(4))
                .andExpect(jsonPath("$.event").value(0))
                .andExpect(jsonPath("$.season").value(0))
                .andRespond(withSuccess("""
                        {
                          "is_anomaly": true,
                          "anomaly_score": 1.9922964233164904,
                          "confidence": 0.6847085559276253,
                          "severity": "MEDIUM",
                          "reason": "Unusual load pattern detected compared to normal Monday behavior.",
                          "model_name": "local_outlier_factor"
                        }
                        """, MediaType.APPLICATION_JSON));

        when(anomalyRepository.save(any(Anomaly.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = anomalyService.detectAnomaly(
                43L, forecastTimestamp, 1948.0, 29.0, 79.0, 0, 14, 1, 4
        );

        assertEquals(true, result.get("is_anomaly"));
        assertEquals("python_model", result.get("source"));
        assertEquals("MEDIUM", result.get("severity"));
        assertEquals("local_outlier_factor", result.get("model_name"));
        assertTrue(((Double) result.get("confidence")) > 0.0);

        verify(anomalyRepository).save(argThat(anomaly ->
                anomaly.getPredictionId().equals(43L)
                        && forecastTimestamp.equals(anomaly.getTimestamp())
                        && anomaly.getConfidence() != null
                        && anomaly.getConfidence() > 0.68
                        && "MEDIUM".equals(anomaly.getSeverity())
                        && "OPEN".equals(anomaly.getStatus())
                        && anomaly.getAnomalyScore() > 1.0
        ));

        mockServer.verify();
    }

    @Test
    void detectAnomaly_NormalPythonResponse_DoesNotSaveAnomaly() {
        LocalDateTime forecastTimestamp = LocalDateTime.of(2026, 4, 13, 10, 0);

        mockServer.expect(requestTo("http://localhost:5002/detect_anomaly"))
                .andRespond(withSuccess("""
                        {
                          "is_anomaly": false,
                          "anomaly_score": 0.221,
                          "confidence": 0.58,
                          "severity": "NORMAL",
                          "reason": "Load behavior matches historical patterns.",
                          "model_name": "local_outlier_factor"
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, Object> result = anomalyService.detectAnomaly(
                52L, forecastTimestamp, 1525.0, 27.0, 74.0, 0, 10, 1, 4
        );

        assertEquals(false, result.get("is_anomaly"));
        assertEquals("NORMAL", result.get("severity"));
        verify(anomalyRepository, never()).save(any(Anomaly.class));

        mockServer.verify();
    }

    @Test
    void detectAnomaly_PythonFailure_FallsBackToRuleBasedAndSaves() {
        LocalDateTime forecastTimestamp = LocalDateTime.of(2026, 6, 4, 14, 0);

        when(loadDataRepository.findAll()).thenReturn(List.of(
                buildLoadData(14, 3200.0),
                buildLoadData(14, 3300.0),
                buildLoadData(14, 3400.0),
                buildLoadData(10, 2800.0)
        ));
        when(anomalyRepository.save(any(Anomaly.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockServer.expect(requestTo("http://localhost:5002/detect_anomaly"))
                .andRespond(withServerError());

        Map<String, Object> result = anomalyService.detectAnomaly(
                77L, forecastTimestamp, 5000.0, 38.0, 90.0, 0, 14, 4, 6
        );

        assertEquals(true, result.get("is_anomaly"));
        assertEquals("rule_based", result.get("source"));
        assertEquals("hour_zscore_fallback", result.get("model_name"));
        assertEquals("HIGH", result.get("severity"));

        verify(anomalyRepository).save(argThat(anomaly ->
                anomaly.getPredictionId().equals(77L)
                        && forecastTimestamp.equals(anomaly.getTimestamp())
                        && anomaly.getConfidence() != null
                        && "HIGH".equals(anomaly.getSeverity())
        ));

        mockServer.verify();
    }

    private LoadData buildLoadData(int hour, double load) {
        LoadData item = new LoadData();
        item.setHourOfDay(hour);
        item.setLoadDemand(load);
        return item;
    }
}
