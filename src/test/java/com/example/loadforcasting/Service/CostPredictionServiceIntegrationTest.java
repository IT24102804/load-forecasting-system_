package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.CostPredictionRun;
import com.example.loadforcasting.Entity.CostPredictionRequestEntity;
import com.example.loadforcasting.Entity.GenerationMixRun;
import com.example.loadforcasting.Entity.ModelVersion;
import com.example.loadforcasting.Repository.CostPredictionRequestRepository;
import com.example.loadforcasting.Repository.CostPredictionRunRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostPredictionServiceIntegrationTest {

    @Mock
    private CostPredictionRunRepository costPredictionRunRepository;

    @Mock
    private CostPredictionRequestRepository costPredictionRequestRepository;

    @Mock
    private PredictionSignatureService predictionSignatureService;

    @Mock
    private ModelVersionService modelVersionService;

    @InjectMocks
    private CostPredictionService costPredictionService;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void predictCost_PythonSuccess_SendsExpectedPayloadAndUpsertsRun() throws Exception {
        stubCanonicalDependencies();
        AtomicReference<String> requestBody = new AtomicReference<>("");
        String url = startServer("""
                {
                  "unit_cost": 52.0
                }
                """, 200, requestBody);
        ReflectionTestUtils.setField(costPredictionService, "costServiceUrl", url);

        when(costPredictionRunRepository.save(any(CostPredictionRun.class)))
                .thenAnswer(invocation -> {
                    CostPredictionRun r = invocation.getArgument(0);
                    r.setId(1L);
                    return r;
                });

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

        Map<String, Object> result = costPredictionService.predictCost(run, 1000.0, 2000.0, 1500.0, 1600.0);

        assertEquals(52.0, ((Number) result.get("unit_cost")).doubleValue(), 0.0001);
        assertEquals(1L, ((Number) result.get("cost_run_id")).longValue());
        assertTrue(requestBody.get().contains("\"date\":\"2025-04-13\""));
        assertTrue(requestBody.get().contains("\"load_demand\":28940.21"));
        assertTrue(requestBody.get().contains("\"fo_price\":1000.0"));
        assertTrue(requestBody.get().contains("\"coal_price\":2000.0"));
        assertTrue(requestBody.get().contains("\"diesel_price\":1500.0"));
        assertTrue(requestBody.get().contains("\"naphtha_price\":1600.0"));
    }

    @Test
    void predictCost_PythonValidationError_ThrowsIllegalArgumentException() throws Exception {
        stubCanonicalDependencies();
        String url = startServer("""
                {"error":"Invalid inputs"}
                """, 400, new AtomicReference<>(""));
        ReflectionTestUtils.setField(costPredictionService, "costServiceUrl", url);

        GenerationMixRun run = new GenerationMixRun();
        run.setId(10L);
        run.setForecastTimestamp(LocalDateTime.of(2025, 4, 13, 14, 0));
        run.setEstimatedDailyDemandMwh(28940.21);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> costPredictionService.predictCost(run, 1000.0, 2000.0, 1500.0, 1600.0));

        assertNotNull(ex.getMessage());
    }

    private String startServer(String responseBody, int statusCode, AtomicReference<String> requestBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/predict", exchange -> handleResponse(exchange, responseBody, statusCode, requestBody));
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/predict";
    }

    private void handleResponse(HttpExchange exchange, String responseBody, int statusCode,
                                AtomicReference<String> requestBody) throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
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
