package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.CostPredictionRun;
import com.example.loadforcasting.Entity.CostPredictionRequestEntity;
import com.example.loadforcasting.Entity.GenerationMixRun;
import com.example.loadforcasting.Entity.ModelVersion;
import com.example.loadforcasting.Repository.CostPredictionRequestRepository;
import com.example.loadforcasting.Repository.CostPredictionRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostPredictionServiceTest {

    @Mock
    private CostPredictionRunRepository costPredictionRunRepository;

    @Mock
    private CostPredictionRequestRepository costPredictionRequestRepository;

    @Mock
    private PredictionSignatureService predictionSignatureService;

    @Mock
    private ModelVersionService modelVersionService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private CostPredictionService costPredictionService;

    private GenerationMixRun mixRun() {
        GenerationMixRun run = new GenerationMixRun();
        run.setId(10L);
        run.setForecastTimestamp(LocalDateTime.of(2025, 4, 13, 14, 0));
        run.setEstimatedDailyDemandMwh(28940.21);
        run.setMajorHydroMwh(8200.0);
        run.setTotalCoalMwh(7100.0);
        run.setTotalThermalMwh(4200.0);
        run.setWindMwh(1450.0);
        run.setSolarMwh(910.0);
        run.setMiniHydroMwh(1800.0);
        return run;
    }

    @Test
    void predictCost_WhenAiSuccess_SavesRunAndReturnsBodyWithCostRunId() {
        stubCanonicalDependencies();
        ReflectionTestUtils.setField(costPredictionService, "costServiceUrl", "http://localhost:5004/predict");
        ReflectionTestUtils.setField(costPredictionService, "restTemplate", restTemplate);

        when(costPredictionRunRepository.save(any(CostPredictionRun.class)))
                .thenAnswer(invocation -> {
                    CostPredictionRun r = invocation.getArgument(0);
                    r.setId(1L);
                    return r;
                });

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unit_cost", 52.0);
        when(restTemplate.exchange(eq("http://localhost:5004/predict"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(body));

        Map<String, Object> result = costPredictionService.predictCost(mixRun(), 1000, 2000, 1500, 1600);

        assertEquals(52.0, ((Number) result.get("unit_cost")).doubleValue(), 0.0001);
        assertEquals(1L, ((Number) result.get("cost_run_id")).longValue());
        verify(costPredictionRunRepository).save(any(CostPredictionRun.class));
    }

    @Test
    void predictCost_WhenRunNull_ThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> costPredictionService.predictCost(null, 1000, 2000, 3000, 4000));
        assertNotNull(ex.getMessage());
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void predictCost_WhenForecastTimestampMissing_ThrowsIllegalArgumentException() {
        stubCanonicalDependencies();
        GenerationMixRun run = new GenerationMixRun();
        run.setId(10L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> costPredictionService.predictCost(run, 1000, 2000, 3000, 4000));
        assertNotNull(ex.getMessage());
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void predictCost_WhenFuelPriceInvalid_ThrowsIllegalArgumentException() {
        stubCanonicalDependencies();
        GenerationMixRun run = mixRun();

        assertThrows(IllegalArgumentException.class,
                () -> costPredictionService.predictCost(run, -1, 2000, 3000, 4000));
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void predictCost_WhenAiReturnsNullBody_ThrowsRuntimeException() {
        stubCanonicalDependencies();
        ReflectionTestUtils.setField(costPredictionService, "costServiceUrl", "http://localhost:5004/predict");
        ReflectionTestUtils.setField(costPredictionService, "restTemplate", restTemplate);

        when(restTemplate.exchange(eq("http://localhost:5004/predict"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThrows(RuntimeException.class,
                () -> costPredictionService.predictCost(mixRun(), 1000, 2000, 1500, 1600));
    }

    @Test
    void predictCost_WhenAiReturns400_ThrowsIllegalArgumentException() {
        stubCanonicalDependencies();
        ReflectionTestUtils.setField(costPredictionService, "costServiceUrl", "http://localhost:5004/predict");
        ReflectionTestUtils.setField(costPredictionService, "restTemplate", restTemplate);

        when(restTemplate.exchange(eq("http://localhost:5004/predict"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad", "{\"error\":\"Invalid inputs\"}".getBytes(), null));

        assertThrows(IllegalArgumentException.class,
                () -> costPredictionService.predictCost(mixRun(), 1000, 2000, 1500, 1600));
    }

    @Test
    void predictCost_WhenServiceUnavailable_ThrowsRuntimeException() {
        stubCanonicalDependencies();
        ReflectionTestUtils.setField(costPredictionService, "costServiceUrl", "http://localhost:5004/predict");
        ReflectionTestUtils.setField(costPredictionService, "restTemplate", restTemplate);

        when(restTemplate.exchange(eq("http://localhost:5004/predict"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new ResourceAccessException("connect timed out"));

        assertThrows(RuntimeException.class,
                () -> costPredictionService.predictCost(mixRun(), 1000, 2000, 1500, 1600));
    }

    @Test
    void updateCostRun_WhenExistingRunNull_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> costPredictionService.updateCostRun(null, mixRun(), 1000, 2000, 3000, 4000));
    }

    @Test
    void deleteCostRun_WhenIdMissing_ThrowsIllegalArgumentException() {
        when(costPredictionRunRepository.existsById(99L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> costPredictionService.deleteCostRun(99L));
        verify(costPredictionRunRepository, never()).deleteById(any());
    }

    @Test
    void executePrediction_BuildsRequestWithMixPredictionValues() {
        stubCanonicalDependencies();
        ReflectionTestUtils.setField(costPredictionService, "costServiceUrl", "http://localhost:5004/predict");
        ReflectionTestUtils.setField(costPredictionService, "restTemplate", restTemplate);

        when(costPredictionRunRepository.save(any(CostPredictionRun.class)))
                .thenAnswer(invocation -> {
                    CostPredictionRun r = invocation.getArgument(0);
                    r.setId(1L);
                    return r;
                });

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unit_cost", 52.0);
        when(restTemplate.exchange(eq("http://localhost:5004/predict"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(body));

        costPredictionService.predictCost(mixRun(), 1000, 2000, 1500, 1600);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq("http://localhost:5004/predict"), eq(HttpMethod.POST), captor.capture(), any(ParameterizedTypeReference.class));

        Map<?, ?> payload = (Map<?, ?>) captor.getValue().getBody();
        assertEquals("2025-04-13", payload.get("date"));
        assertEquals(28940.21, ((Number) payload.get("load_demand")).doubleValue(), 0.0001);
        assertEquals(8200.0, ((Number) payload.get("major_hydro")).doubleValue(), 0.0001);
        assertEquals(7100.0, ((Number) payload.get("total_coal")).doubleValue(), 0.0001);
        assertEquals(4200.0, ((Number) payload.get("total_thermal")).doubleValue(), 0.0001);
        assertEquals(1450.0, ((Number) payload.get("wind")).doubleValue(), 0.0001);
        assertEquals(910.0, ((Number) payload.get("solar")).doubleValue(), 0.0001);
        assertEquals(1800.0, ((Number) payload.get("mini_hydro")).doubleValue(), 0.0001);
        assertEquals(1000.0, ((Number) payload.get("fo_price")).doubleValue(), 0.0001);
        assertEquals(2000.0, ((Number) payload.get("coal_price")).doubleValue(), 0.0001);
        assertEquals(1500.0, ((Number) payload.get("diesel_price")).doubleValue(), 0.0001);
        assertEquals(1600.0, ((Number) payload.get("naphtha_price")).doubleValue(), 0.0001);
    }

    private void stubCanonicalDependencies() {
        ModelVersion modelVersion = new ModelVersion();
        modelVersion.setId(1L);
        modelVersion.setVersionLabel("v1");
        modelVersion.setModelName("cost_prediction_ai");

        when(predictionSignatureService.buildCostScenarioSignature(eq(10L), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("cost-signature");
        when(costPredictionRequestRepository.findByScenarioSignature("cost-signature")).thenReturn(Optional.empty());
        when(costPredictionRequestRepository.save(any(CostPredictionRequestEntity.class))).thenAnswer(invocation -> {
            CostPredictionRequestEntity saved = invocation.getArgument(0);
            saved.setId(300L);
            return saved;
        });
        when(modelVersionService.resolveCurrent(anyString(), anyString())).thenReturn(modelVersion);
        when(costPredictionRunRepository.findFirstByCostPredictionRequestAndModelVersionOrderByCreatedAtDesc(any(CostPredictionRequestEntity.class), any(ModelVersion.class)))
                .thenReturn(Optional.empty());
    }
}
