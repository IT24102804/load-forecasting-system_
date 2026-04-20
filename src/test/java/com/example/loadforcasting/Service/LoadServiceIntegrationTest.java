package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Entity.LoadForecast;
import com.example.loadforcasting.Entity.LoadForecastRun;
import com.example.loadforcasting.Entity.ModelVersion;
import com.example.loadforcasting.Repository.LoadForecastRepository;
import com.example.loadforcasting.Repository.LoadForecastRunRepository;
import com.example.loadforcasting.Repository.LoadRepository;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoadServiceIntegrationTest {

    @Mock
    private LoadRepository loadRepository;

    @Mock
    private LoadForecastRepository loadForecastRepository;

    @Mock
    private LoadForecastRunRepository loadForecastRunRepository;

    @Mock
    private PredictionSignatureService predictionSignatureService;

    @Mock
    private ModelVersionService modelVersionService;

    @InjectMocks
    private LoadService loadService;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void predictAndSave_PythonSuccess_SendsExpectedPayloadAndPersistsPrediction() throws Exception {
        stubCanonicalDependencies();
        AtomicReference<String> requestBody = new AtomicReference<>("");
        String url = startServer("""
                {
                  "load_demand": 1205.842041015625
                }
                """, 200, requestBody);
        ReflectionTestUtils.setField(loadService, "PYTHON_LOAD_API_URL", url);

        when(loadRepository.save(any(LoadRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoadRequest request = new LoadRequest();
        request.setTimestamp(LocalDateTime.of(2026, 4, 13, 14, 0));
        request.setTemperature(28.0);
        request.setHumidity(80.0);
        request.setPublicEvent(1);

        double result = loadService.predictAndSave(request);

        assertEquals(1205.842041015625, result, 0.000001);
        assertEquals(1205.842041015625, request.getPredictedLoad(), 0.000001);
        assertTrue(requestBody.get().contains("\"timestamp\":\"2026-04-13T14:00\""));
        assertTrue(requestBody.get().contains("\"temperature\":28.0"));
        assertTrue(requestBody.get().contains("\"humidity\":80.0"));
        assertTrue(requestBody.get().contains("\"public_event\":1"));

        verify(loadRepository).save(argThat(saved ->
                saved.getPublicEvent() == 1
                        && saved.getPredictedLoad() == 1205.842041015625
        ));
    }

    @Test
    void predictAndSave_InvalidPythonResponse_UsesFallbackAndPersistsPrediction() throws Exception {
        stubCanonicalDependencies();
        String url = startServer("""
                {
                  "unexpected": 1
                }
                """, 200, new AtomicReference<>(""));
        ReflectionTestUtils.setField(loadService, "PYTHON_LOAD_API_URL", url);

        when(loadRepository.save(any(LoadRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoadRequest request = new LoadRequest();
        request.setTimestamp(LocalDateTime.of(2026, 4, 13, 14, 0));
        request.setTemperature(28.0);
        request.setHumidity(80.0);
        request.setPublicEvent(1);

        double result = loadService.predictAndSave(request);

        assertEquals(1940.0, result, 0.0001);
        assertEquals(1940.0, request.getPredictedLoad(), 0.0001);

        verify(loadRepository).save(argThat(saved ->
                saved.getPredictedLoad() == 1940.0
                        && saved.getTemperature() == 28.0
                        && saved.getHumidity() == 80.0
        ));
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
        modelVersion.setModelName("load_ai_predictor");

        when(predictionSignatureService.buildLoadSignature(any(LocalDateTime.class), anyDouble(), anyDouble(), anyInt()))
                .thenReturn("load-signature");
        when(modelVersionService.resolveCurrent(anyString(), anyString())).thenReturn(modelVersion);
        when(loadForecastRepository.findByInputSignature("load-signature")).thenReturn(Optional.empty());
        when(loadForecastRepository.save(any(LoadForecast.class))).thenAnswer(invocation -> {
            LoadForecast saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });
        when(loadForecastRunRepository.findFirstByLoadForecastAndModelVersionOrderByCreatedAtDesc(any(LoadForecast.class), any(ModelVersion.class)))
                .thenReturn(Optional.empty());
        when(loadForecastRunRepository.save(any(LoadForecastRun.class))).thenAnswer(invocation -> {
            LoadForecastRun saved = invocation.getArgument(0);
            saved.setId(200L);
            return saved;
        });
    }
}
