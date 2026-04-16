package com.example.loadforcasting.systemflow.support;

import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Repository.AnomalyRepository;
import com.example.loadforcasting.Repository.FeedbackRepository;
import com.example.loadforcasting.Repository.LoadDataRepository;
import com.example.loadforcasting.Repository.LoadRepository;
import com.example.loadforcasting.Repository.UserRepository;
import com.example.loadforcasting.Repository.WeatherPredictionRepository;
import com.example.loadforcasting.Service.GenerationMixService;
import com.example.loadforcasting.Service.LoadService;
import com.example.loadforcasting.Service.WeatherPredictionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("systemtest")
public abstract class AbstractSystemFlowTest {

    private static final AiStubServers AI_STUBS = new AiStubServers();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected LoadService loadService;

    @Autowired
    protected WeatherPredictionService weatherPredictionService;

    @Autowired
    protected GenerationMixService generationMixService;

    @Autowired
    protected LoadRepository loadRepository;

    @Autowired
    protected AnomalyRepository anomalyRepository;

    @Autowired
    protected FeedbackRepository feedbackRepository;

    @Autowired
    protected WeatherPredictionRepository weatherPredictionRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected LoadDataRepository loadDataRepository;

    @DynamicPropertySource
    static void configureDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("anomaly.service.url", AI_STUBS::anomalyBaseUrl);
    }

    @BeforeEach
    void prepareSystemFlowEnvironment() {
        ReflectionTestUtils.setField(loadService, "PYTHON_LOAD_API_URL", AI_STUBS.loadPredictUrl());
        ReflectionTestUtils.setField(weatherPredictionService, "PYTHON_API_URL", AI_STUBS.weatherPredictUrl());
        ReflectionTestUtils.setField(generationMixService, "generationMixServiceUrl", AI_STUBS.generationMixPredictUrl());

        AI_STUBS.reset();

        anomalyRepository.deleteAll();
        feedbackRepository.deleteAll();
        loadRepository.deleteAll();
        weatherPredictionRepository.deleteAll();
        loadDataRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected void stubLoadPrediction(double loadDemand) {
        AI_STUBS.stubLoadPredict(body -> StubResponse.json(200, """
                {
                  "load_demand": %s
                }
                """.formatted(loadDemand)));
    }

    protected void stubWeatherPrediction(double temperature, double humidity,
                                         double windSpeed, double rainfall,
                                         double solarIrradiance) {
        AI_STUBS.stubWeatherPredict(body -> StubResponse.json(200, """
                {
                  "temperature": %s,
                  "humidity": %s,
                  "wind_speed": %s,
                  "rainfall": %s,
                  "solar_irradiance": %s
                }
                """.formatted(temperature, humidity, windSpeed, rainfall, solarIrradiance)));
    }

    protected void stubAnomalyDetection(boolean isAnomaly, double anomalyScore,
                                        double confidence, String severity,
                                        String reason, String modelName) {
        AI_STUBS.stubAnomalyDetect(body -> StubResponse.json(200, """
                {
                  "is_anomaly": %s,
                  "anomaly_score": %s,
                  "confidence": %s,
                  "severity": "%s",
                  "reason": "%s",
                  "model_name": "%s"
                }
                """.formatted(isAnomaly, anomalyScore, confidence,
                escapeJson(severity), escapeJson(reason), escapeJson(modelName))));
    }

    protected void stubAnomalyFeedbackAccepted() {
        AI_STUBS.stubAnomalyFeedback(body -> StubResponse.json(200, """
                {
                  "status": "logged"
                }
                """));
    }

    protected void stubGenerationMixPrediction(double totalMwh, double majorHydro,
                                               double totalCoal, double totalThermal,
                                               double wind, double solar,
                                               double miniHydro) {
        double total = totalMwh <= 0
                ? Math.max(majorHydro + totalCoal + totalThermal + wind + solar + miniHydro, 1.0)
                : totalMwh;
        AI_STUBS.stubGenerationMixPredict(body -> StubResponse.json(200, """
                {
                  "date": "2026-04-13",
                  "inputs_used": {
                    "wind_lag1": %s,
                    "solar_lag1": %s,
                    "mini_hydro_lag1": %s
                  },
                  "prediction": {
                    "major_hydro": %s,
                    "total_coal": %s,
                    "total_thermal": %s,
                    "wind": %s,
                    "solar": %s,
                    "mini_hydro": %s
                  },
                  "percentages": {
                    "major_hydro": %s,
                    "total_coal": %s,
                    "total_thermal": %s,
                    "wind": %s,
                    "solar": %s,
                    "mini_hydro": %s
                  },
                  "total_mwh": %s
                }
                """.formatted(
                wind, solar, miniHydro,
                majorHydro, totalCoal, totalThermal, wind, solar, miniHydro,
                roundPercent(majorHydro, total), roundPercent(totalCoal, total),
                roundPercent(totalThermal, total), roundPercent(wind, total),
                roundPercent(solar, total), roundPercent(miniHydro, total),
                total
        )));
    }

    protected int anomalyFeedbackHitCount() {
        return AI_STUBS.anomalyFeedbackHitCount();
    }

    protected String lastAnomalyFeedbackRequestBody() {
        return AI_STUBS.lastAnomalyFeedbackBody();
    }

    protected String lastGenerationMixRequestBody() {
        return AI_STUBS.lastGenerationMixPredictBody();
    }

    protected MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userid", 1);
        session.setAttribute("role", "Admin");
        return session;
    }

    protected String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    protected LoadRequest firstLoadRequest() {
        return loadRepository.findAll().stream().findFirst().orElseThrow();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record StubResponse(int statusCode, String body, String contentType) {
        static StubResponse json(int statusCode, String body) {
            return new StubResponse(statusCode, body, MediaType.APPLICATION_JSON_VALUE);
        }
    }

    private static final class AiStubServers {
        private final StubEndpoint loadPredict = new StubEndpoint();
        private final StubEndpoint weatherPredict = new StubEndpoint();
        private final StubEndpoint anomalyDetect = new StubEndpoint();
        private final StubEndpoint anomalyFeedback = new StubEndpoint();
        private final StubEndpoint generationMixPredict = new StubEndpoint();

        private final HttpServer loadServer;
        private final HttpServer weatherServer;
        private final HttpServer anomalyServer;
        private final HttpServer generationMixServer;

        private AiStubServers() {
            try {
                loadServer = HttpServer.create(new InetSocketAddress(0), 0);
                loadServer.createContext("/predict", loadPredict);
                loadServer.start();

                weatherServer = HttpServer.create(new InetSocketAddress(0), 0);
                weatherServer.createContext("/predict", weatherPredict);
                weatherServer.start();

                anomalyServer = HttpServer.create(new InetSocketAddress(0), 0);
                anomalyServer.createContext("/detect_anomaly", anomalyDetect);
                anomalyServer.createContext("/anomaly_feedback", anomalyFeedback);
                anomalyServer.start();

                generationMixServer = HttpServer.create(new InetSocketAddress(0), 0);
                generationMixServer.createContext("/predict", generationMixPredict);
                generationMixServer.start();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to start AI stub servers", e);
            }
            reset();
        }

        private void reset() {
            stubLoadPredict(body -> StubResponse.json(200, """
                    {
                      "load_demand": 1500.0
                    }
                    """));
            stubWeatherPredict(body -> StubResponse.json(200, """
                    {
                      "temperature": 28.0,
                      "humidity": 75.0,
                      "wind_speed": 3.0,
                      "rainfall": 0.0,
                      "solar_irradiance": 500.0
                    }
                    """));
            stubAnomalyDetect(body -> StubResponse.json(200, """
                    {
                      "is_anomaly": false,
                      "anomaly_score": 0.15,
                      "confidence": 0.55,
                      "severity": "NORMAL",
                      "reason": "Normal operating context.",
                      "model_name": "local_outlier_factor"
                    }
                    """));
            stubAnomalyFeedback(body -> StubResponse.json(200, """
                    {
                      "status": "logged"
                    }
                    """));
            stubGenerationMixPredict(body -> StubResponse.json(200, """
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
                    """));
        }

        private String loadPredictUrl() {
            return "http://localhost:" + loadServer.getAddress().getPort() + "/predict";
        }

        private String weatherPredictUrl() {
            return "http://localhost:" + weatherServer.getAddress().getPort() + "/predict";
        }

        private String anomalyBaseUrl() {
            return "http://localhost:" + anomalyServer.getAddress().getPort();
        }

        private String generationMixPredictUrl() {
            return "http://localhost:" + generationMixServer.getAddress().getPort() + "/predict";
        }

        private void stubLoadPredict(Function<String, StubResponse> responder) {
            loadPredict.setResponder(responder);
        }

        private void stubWeatherPredict(Function<String, StubResponse> responder) {
            weatherPredict.setResponder(responder);
        }

        private void stubAnomalyDetect(Function<String, StubResponse> responder) {
            anomalyDetect.setResponder(responder);
        }

        private void stubAnomalyFeedback(Function<String, StubResponse> responder) {
            anomalyFeedback.setResponder(responder);
        }

        private void stubGenerationMixPredict(Function<String, StubResponse> responder) {
            generationMixPredict.setResponder(responder);
        }

        private int anomalyFeedbackHitCount() {
            return anomalyFeedback.hitCount();
        }

        private String lastAnomalyFeedbackBody() {
            return anomalyFeedback.lastRequestBody();
        }

        private String lastGenerationMixPredictBody() {
            return generationMixPredict.lastRequestBody();
        }
    }

    private static final class StubEndpoint implements HttpHandler {
        private final AtomicReference<Function<String, StubResponse>> responder =
                new AtomicReference<>(body -> StubResponse.json(200, "{}"));
        private final AtomicReference<String> lastRequestBody = new AtomicReference<>("");
        private final AtomicInteger hitCount = new AtomicInteger();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastRequestBody.set(body);
            hitCount.incrementAndGet();

            StubResponse response = responder.get().apply(body);
            byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
            exchange.sendResponseHeaders(response.statusCode(), payload.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }

        private void setResponder(Function<String, StubResponse> newResponder) {
            responder.set(newResponder);
            lastRequestBody.set("");
            hitCount.set(0);
        }

        private int hitCount() {
            return hitCount.get();
        }

        private String lastRequestBody() {
            return lastRequestBody.get();
        }
    }

    private static double roundPercent(double value, double total) {
        return Math.round((value / total) * 1000.0) / 10.0;
    }
}
