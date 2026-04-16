package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.WeatherPrediction;
import com.example.loadforcasting.Repository.WeatherPredictionRepository;
import com.example.loadforcasting.dto.WeatherPredictionResult;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherPredictionServiceIntegrationTest {

    @Mock
    private WeatherPredictionRepository repository;

    @InjectMocks
    private WeatherPredictionService service;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void predictTransient_PythonSuccess_ReturnsWeatherWithoutPersisting() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        String url = startServer("""
                {
                  "temperature": 29.4,
                  "humidity": 78.1,
                  "wind_speed": 3.2,
                  "rainfall": 1.5,
                  "solar_irradiance": 512.4
                }
                """, 200, requestBody);
        ReflectionTestUtils.setField(service, "PYTHON_API_URL", url);

        WeatherPredictionResult result = service.predictTransient("2026-04-13T14:30:00");

        assertEquals(LocalDate.of(2026, 4, 13), result.predictionDateTime().toLocalDate());
        assertEquals(LocalTime.of(14, 30), result.predictionDateTime().toLocalTime());
        assertEquals(29.4, result.temperature(), 0.0001);
        assertEquals(78.1, result.humidity(), 0.0001);
        assertEquals(3.2, result.windSpeed(), 0.0001);
        assertEquals(1.5, result.rainfall(), 0.0001);
        assertEquals(512.4, result.solarIrradiance(), 0.0001);
        assertEquals("python_model", result.source());
        assertTrue(requestBody.get().contains("\"date_time\":\"2026-04-13T14:30:00\""));
        verify(repository, never()).save(any(WeatherPrediction.class));
    }

    @Test
    void predictTransient_PythonOffline_UsesFallbackWithoutPersisting() {
        ReflectionTestUtils.setField(service, "PYTHON_API_URL", "http://localhost:1/predict");

        WeatherPredictionResult result = service.predictTransient("2026-04-13T14:30:00");

        assertEquals(LocalDate.of(2026, 4, 13), result.predictionDateTime().toLocalDate());
        assertEquals(LocalTime.of(14, 30), result.predictionDateTime().toLocalTime());
        assertTrue(result.temperature() >= 26.0 && result.temperature() <= 34.0);
        assertTrue(result.humidity() >= 65.0 && result.humidity() <= 85.0);
        assertTrue(result.windSpeed() >= 1.5 && result.windSpeed() <= 5.5);
        assertTrue(result.rainfall() >= 0.0 && result.rainfall() <= 12.0);
        assertTrue(result.solarIrradiance() >= 400.0 && result.solarIrradiance() <= 700.0);
        assertEquals("fallback", result.source());
        verify(repository, never()).save(any(WeatherPrediction.class));
    }

    @Test
    void predictAndSave_PythonSuccess_MapsAllWeatherFieldsAndPersists() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        String url = startServer("""
                {
                  "temperature": 29.4,
                  "humidity": 78.1,
                  "wind_speed": 3.2,
                  "rainfall": 1.5,
                  "solar_irradiance": 512.4
                }
                """, 200, requestBody);
        ReflectionTestUtils.setField(service, "PYTHON_API_URL", url);

        when(repository.save(any(WeatherPrediction.class))).thenAnswer(invocation -> {
            WeatherPrediction saved = invocation.getArgument(0);
            saved.setId(14L);
            return saved;
        });

        WeatherPrediction result = service.predictAndSave("2026-04-13T14:30");

        assertEquals(14L, result.getId());
        assertEquals(LocalDate.of(2026, 4, 13), result.getPredictionDate());
        assertEquals(LocalTime.of(14, 30), result.getPredictionTime());
        assertEquals(29.4, result.getTemperature(), 0.0001);
        assertEquals(78.1, result.getHumidity(), 0.0001);
        assertEquals(3.2, result.getWindSpeed(), 0.0001);
        assertEquals(1.5, result.getRainfall(), 0.0001);
        assertEquals(512.4, result.getSolarIrradiance(), 0.0001);
        assertTrue(requestBody.get().contains("\"date_time\":\"2026-04-13T14:30\""));

        verify(repository).save(argThat(saved ->
                saved.getPredictionDate().equals(LocalDate.of(2026, 4, 13))
                        && saved.getPredictionTime().equals(LocalTime.of(14, 30))
                        && saved.getSolarIrradiance().equals(512.4)
        ));
    }

    @Test
    void updatePrediction_PythonSuccess_UpdatesExistingRecord() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        String url = startServer("""
                {
                  "temperature": 31.2,
                  "humidity": 74.8,
                  "wind_speed": 2.6,
                  "rainfall": 0.0,
                  "solar_irradiance": 600.0
                }
                """, 200, requestBody);
        ReflectionTestUtils.setField(service, "PYTHON_API_URL", url);

        WeatherPrediction existing = new WeatherPrediction();
        existing.setId(22L);
        existing.setPredictionDate(LocalDate.of(2026, 4, 12));
        existing.setPredictionTime(LocalTime.of(10, 0));

        when(repository.findById(22L)).thenReturn(Optional.of(existing));
        when(repository.save(any(WeatherPrediction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WeatherPrediction updated = service.updatePrediction(22L, "2026-04-14T09:15");

        assertEquals(LocalDate.of(2026, 4, 14), updated.getPredictionDate());
        assertEquals(LocalTime.of(9, 15), updated.getPredictionTime());
        assertEquals(31.2, updated.getTemperature(), 0.0001);
        assertEquals(74.8, updated.getHumidity(), 0.0001);
        assertEquals(2.6, updated.getWindSpeed(), 0.0001);
        assertEquals(0.0, updated.getRainfall(), 0.0001);
        assertEquals(600.0, updated.getSolarIrradiance(), 0.0001);
        assertTrue(requestBody.get().contains("\"date_time\":\"2026-04-14T09:15\""));
    }

    @Test
    void predictAndSave_PythonOffline_UsesFallbackRangesAndPersists() {
        ReflectionTestUtils.setField(service, "PYTHON_API_URL", "http://localhost:1/predict");

        when(repository.save(any(WeatherPrediction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WeatherPrediction result = service.predictAndSave("2026-04-13T14:30");

        assertEquals(LocalDate.of(2026, 4, 13), result.getPredictionDate());
        assertEquals(LocalTime.of(14, 30), result.getPredictionTime());
        assertTrue(result.getTemperature() >= 26.0 && result.getTemperature() <= 34.0);
        assertTrue(result.getHumidity() >= 65.0 && result.getHumidity() <= 85.0);
        assertTrue(result.getWindSpeed() >= 1.5 && result.getWindSpeed() <= 5.5);
        assertTrue(result.getRainfall() >= 0.0 && result.getRainfall() <= 12.0);
        assertTrue(result.getSolarIrradiance() >= 400.0 && result.getSolarIrradiance() <= 700.0);

        verify(repository).save(any(WeatherPrediction.class));
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
