package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.LoadForecast;
import com.example.loadforcasting.Entity.LoadForecastRun;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Entity.ModelVersion;
import com.example.loadforcasting.Repository.LoadForecastRepository;
import com.example.loadforcasting.Repository.LoadForecastRunRepository;
import com.example.loadforcasting.Repository.LoadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class LoadService {

    @Autowired
    private LoadRepository loadRepository;

    @Autowired
    private LoadForecastRepository loadForecastRepository;

    @Autowired
    private LoadForecastRunRepository loadForecastRunRepository;

    @Autowired
    private PredictionSignatureService predictionSignatureService;

    @Autowired
    private ModelVersionService modelVersionService;

    @SuppressWarnings("FieldCanBeLocal")
    private String PYTHON_LOAD_API_URL = "http://localhost:5001/predict";

    @Transactional
    public double predictAndSave(LoadRequest request) {
        return predictAndSave(request, false);
    }

    @Transactional
    public double predictAndSave(LoadRequest request, boolean forceNew) {
        if (request.getTimestamp() == null) {
            throw new IllegalArgumentException("timestamp is required");
        }

        String signature = predictionSignatureService.buildLoadSignature(
                request.getTimestamp(),
                request.getTemperature(),
                request.getHumidity(),
                request.getPublicEvent()
        );
        request.setInputSignature(signature);

        ModelVersion modelVersion = modelVersionService.resolveCurrent(
                ModelVersionService.MODULE_LOAD,
                "load_ai_predictor"
        );

        LoadForecast loadForecast = loadForecastRepository.findByInputSignature(signature)
                .orElseGet(() -> createLoadForecast(request, signature));

        Optional<LoadForecastRun> latestRun = loadForecastRunRepository
                .findFirstByLoadForecastAndModelVersionOrderByCreatedAtDesc(loadForecast, modelVersion);

        LoadForecastRun forecastRun;
        if (latestRun.isPresent() && !forceNew) {
            forecastRun = latestRun.get();
            hydrateLegacyRequest(request, loadForecast, forecastRun, modelVersion, true);
            request.setPredictedLoad(forecastRun.getPredictedLoadKw());
            request.setEstimatedDailyDemandMwh(forecastRun.getEstimatedDailyDemandMwh());
        } else {
            PredictionResult result = predictResult(request);
            forecastRun = new LoadForecastRun();
            forecastRun.setLoadForecast(loadForecast);
            forecastRun.setModelVersion(modelVersion);
            forecastRun.setPredictedLoadKw(result.predictedLoad());
            forecastRun.setEstimatedDailyDemandMwh(round(result.predictedLoad() * 24.0));
            forecastRun.setDailyDemandMethod("instant_forecast_x24");
            forecastRun.setSource(result.source());
            forecastRun.setRunReason(forceNew ? "FORCED_RERUN" : "NEW_SIGNATURE");
            forecastRun.setReused(false);
            forecastRun = loadForecastRunRepository.save(forecastRun);

            request.setPredictedLoad(result.predictedLoad());
            request.setEstimatedDailyDemandMwh(forecastRun.getEstimatedDailyDemandMwh());
            hydrateLegacyRequest(request, loadForecast, forecastRun, modelVersion, false);
        }

        LoadRequest saved = loadRepository.save(request);
        request.setId(saved.getId());
        return saved.getPredictedLoad();
    }

    public double predictValue(LoadRequest request) {
        return predictResult(request).predictedLoad();
    }

    @Transactional
    public LoadRequest repredictAndUpdate(LoadRequest request, boolean forceNew) {
        predictAndSave(request, forceNew);
        return loadRepository.save(request);
    }

    @Transactional
    public void updateRequestWithAnomalyInfo(LoadRequest request) {
        loadRepository.save(request);
    }

    public LoadRequest getRequestById(Long id) {
        Optional<LoadRequest> result = loadRepository.findById(id);
        return result.orElse(null);
    }

    public LoadForecastRun getLoadForecastRunForRequest(Long requestId) {
        return loadRepository.findById(requestId)
                .map(LoadRequest::getLoadForecastRunId)
                .flatMap(loadForecastRunRepository::findById)
                .orElse(null);
    }

    @Transactional
    public void updateFeedback(Long id, boolean agreed) {
        LoadRequest request = loadRepository.findById(id).orElse(null);
        if (request != null) {
            request.setFeedbackGiven(true);
            request.setFeedbackAgreed(agreed);
            loadRepository.save(request);
        }
    }

    private LoadForecast createLoadForecast(LoadRequest request, String signature) {
        LoadForecast loadForecast = new LoadForecast();
        loadForecast.setInputSignature(signature);
        loadForecast.setForecastTimestamp(request.getTimestamp());
        loadForecast.setTemperatureInput(request.getTemperature());
        loadForecast.setHumidityInput(request.getHumidity());
        loadForecast.setPublicEventFlag(request.getPublicEvent());
        return loadForecastRepository.save(loadForecast);
    }

    private void hydrateLegacyRequest(LoadRequest request,
                                      LoadForecast loadForecast,
                                      LoadForecastRun forecastRun,
                                      ModelVersion modelVersion,
                                      boolean reused) {
        request.setLoadForecastId(loadForecast.getId());
        request.setLoadForecastRunId(forecastRun.getId());
        request.setModelVersionLabel(modelVersion.getVersionLabel());
        request.setSource(forecastRun.getSource());
        request.setRunReused(reused);
    }

    private PredictionResult predictResult(LoadRequest request) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> apiRequest = new HashMap<>();
            apiRequest.put("timestamp", request.getTimestamp() != null ? request.getTimestamp().toString() : "2026-04-03T12:00:00");
            apiRequest.put("temperature", request.getTemperature());
            apiRequest.put("humidity", request.getHumidity());
            apiRequest.put("public_event", request.getPublicEvent());

            Map<String, Object> response = restTemplate.postForObject(PYTHON_LOAD_API_URL, apiRequest, Map.class);

            if (response != null && response.containsKey("load_demand")) {
                double prediction = ((Number) response.get("load_demand")).doubleValue();
                return new PredictionResult(prediction, String.valueOf(response.getOrDefault("source", "python_model")));
            }

            throw new RuntimeException("Invalid response from Load AI");
        } catch (Exception e) {
            double prediction = 1500.0 + (request.getTemperature() * 10) + (request.getHumidity() * 2);
            return new PredictionResult(prediction, "fallback");
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record PredictionResult(double predictedLoad, String source) {
    }
}
