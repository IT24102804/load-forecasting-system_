package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.LoadRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class GenerationMixServiceIntegrationTest {

    @InjectMocks
    private GenerationMixService generationMixService;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void predictGenerationMix_UsesDerivedDailyDemandAndMapsSuccessResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        String url = startServer("""
                {
                  "date": "2026-04-13",
                  "inputs_used": {
                    "wind_lag1": 1450.0,
                    "solar_lag1": 910.0,
                    "mini_hydro_lag1": 1800.0
                  },
                  "prediction": {
                    "major_hydro": 8200.0,
                    "total_coal": 7100.0,
                    "total_thermal": 4200.0,
                    "wind": 1450.0,
                    "solar": 910.0,
                    "mini_hydro": 1800.0
                  },
                  "percentages": {
                    "major_hydro": 34.6,
                    "total_coal": 29.9,
                    "total_thermal": 17.7,
                    "wind": 6.1,
                    "solar": 3.8,
                    "mini_hydro": 7.6
                  },
                  "total_mwh": 23660.0
                }
                """, 200, requestBody);
        ReflectionTestUtils.setField(generationMixService, "generationMixServiceUrl", url);

        LoadRequest request = new LoadRequest();
        request.setId(60L);
        request.setTimestamp(LocalDateTime.of(2026, 4, 13, 14, 0));
        request.setPredictedLoad(1205.842041015625);

        Map<String, Object> result = generationMixService.predictGenerationMix(request, 72.5);

        assertEquals(60L, result.get("prediction_id"));
        assertEquals(1205.84, ((Number) result.get("forecast_load")).doubleValue(), 0.0001);
        assertEquals(28940.21, ((Number) result.get("estimated_load_demand")).doubleValue(), 0.0001);
        assertEquals(23660.0, ((Number) result.get("total_mwh")).doubleValue(), 0.0001);
        assertTrue(requestBody.get().contains("\"date\":\"2026-04-13\""));
        assertTrue(requestBody.get().contains("\"reservoir_pct\":72.5"));
        assertTrue(requestBody.get().contains("\"load_demand\":28940.21"));
    }

    @Test
    void predictGenerationMix_FlaskValidationError_ReturnsControlledMessage() throws Exception {
        String url = startServer("""
                {
                  "error": "Load Demand must be at least 20000 MWh!"
                }
                """, 400, new AtomicReference<>(""));
        ReflectionTestUtils.setField(generationMixService, "generationMixServiceUrl", url);

        LoadRequest request = new LoadRequest();
        request.setId(61L);
        request.setTimestamp(LocalDateTime.of(2026, 4, 13, 14, 0));
        request.setPredictedLoad(100.0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> generationMixService.predictGenerationMix(request, 75.0));

        assertEquals("Load Demand must be at least 20000 MWh!", exception.getMessage());
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
}
