package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.GenerationMixRequestEntity;
import com.example.loadforcasting.Entity.GenerationMixRun;
import com.example.loadforcasting.Entity.LoadForecastRun;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Entity.ModelVersion;
import com.example.loadforcasting.Repository.GenerationMixRequestRepository;
import com.example.loadforcasting.Repository.GenerationMixRunRepository;
import com.example.loadforcasting.Repository.LoadForecastRunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GenerationMixService {

    @Value("${generationmix.service.url:http://localhost:5003/predict}")
    private String generationMixServiceUrl;

    @Autowired
    private LoadService loadService;

    @Autowired
    private LoadForecastRunRepository loadForecastRunRepository;

    @Autowired
    private GenerationMixRunRepository generationMixRunRepository;

    @Autowired
    private GenerationMixRequestRepository generationMixRequestRepository;

    @Autowired
    private PredictionSignatureService predictionSignatureService;

    @Autowired
    private ModelVersionService modelVersionService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Double> estimatedDailyDemandCache = new ConcurrentHashMap<>();

    public Map<String, Object> predictGenerationMix(LoadRequest loadRequest, double reservoirPct, boolean forceNew) {
        if (loadRequest == null || loadRequest.getId() == null) {
            throw new IllegalArgumentException("A saved forecast is required before generating the mix.");
        }
        if (loadRequest.getTimestamp() == null) {
            throw new IllegalArgumentException("Forecast timestamp is missing for the selected prediction.");
        }
        if (reservoirPct < 0 || reservoirPct > 100) {
            throw new IllegalArgumentException("Reservoir percentage must be between 0 and 100.");
        }

        LoadForecastRun loadForecastRun = loadService.getLoadForecastRunForRequest(loadRequest.getId());
        if (loadForecastRun == null) {
            throw new IllegalArgumentException("A saved forecast run is required before generating the mix.");
        }

        Double reservoirKey = round(reservoirPct);
        String scenarioSignature = predictionSignatureService.buildGenerationMixScenarioSignature(loadForecastRun.getId(), reservoirKey);
        GenerationMixRequestEntity requestEntity = generationMixRequestRepository.findByScenarioSignature(scenarioSignature)
                .orElseGet(() -> createGenerationMixRequest(loadForecastRun, scenarioSignature, reservoirKey));

        ModelVersion modelVersion = modelVersionService.resolveCurrent(
                ModelVersionService.MODULE_GENERATION_MIX,
                "generation_mix_ai"
        );

        Optional<GenerationMixRun> existing = generationMixRunRepository
                .findFirstByGenerationMixRequestAndModelVersionOrderByCreatedAtDesc(requestEntity, modelVersion);

        if (existing.isPresent() && !forceNew) {
            return buildPayloadFromExistingRun(existing.get(), true);
        }

        double estimatedLoadDemand = resolveEstimatedDailyDemand(loadRequest, loadForecastRun);
        Map<String, Object> result = generateMixResult(loadRequest, reservoirKey, estimatedLoadDemand);
        GenerationMixRun saved = saveRun(loadRequest, loadForecastRun, requestEntity, modelVersion, reservoirKey, estimatedLoadDemand, result, forceNew);
        return buildPayloadFromExistingRun(saved, false);
    }

    public Map<String, Object> predictGenerationMix(LoadRequest loadRequest, double reservoirPct) {
        return predictGenerationMix(loadRequest, reservoirPct, false);
    }

    public List<GenerationMixRun> getAllRuns() {
        return generationMixRunRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<GenerationMixRun> getRunById(Long id) {
        return generationMixRunRepository.findById(id);
    }

    public double getEstimatedDailyDemandForView(LoadRequest loadRequest) {
        if (loadRequest == null) {
            throw new IllegalArgumentException("Forecast is required before generating the mix.");
        }

        LoadForecastRun loadForecastRun = loadRequest.getId() != null ? loadService.getLoadForecastRunForRequest(loadRequest.getId()) : null;
        if (loadForecastRun != null && loadForecastRun.getEstimatedDailyDemandMwh() > 0) {
            estimatedDailyDemandCache.put(loadForecastRun.getId(), loadForecastRun.getEstimatedDailyDemandMwh());
            return round(loadForecastRun.getEstimatedDailyDemandMwh());
        }

        if (loadRequest.getPredictedLoad() <= 0) {
            throw new IllegalArgumentException("Forecast load must be available before generating the mix.");
        }

        return round(loadRequest.getPredictedLoad() * 24.0);
    }

    public void deleteRun(Long id) {
        generationMixRunRepository.deleteById(id);
    }

    public GenerationMixRun rerunAndUpdate(Long runId, LoadRequest loadRequest, double reservoirPct) {
        GenerationMixRun run = generationMixRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Saved mix not found: " + runId));

        LoadForecastRun loadForecastRun = loadService.getLoadForecastRunForRequest(loadRequest.getId());
        if (loadForecastRun == null) {
            throw new IllegalArgumentException("Forecast run not found for prediction " + loadRequest.getId());
        }

        double reservoirKey = round(reservoirPct);
        double estimatedLoadDemand = resolveEstimatedDailyDemand(loadRequest, loadForecastRun);
        Map<String, Object> result = generateMixResult(loadRequest, reservoirKey, estimatedLoadDemand);

        run.setLoadRequest(loadRequest);
        run.setGenerationMixRequest(createGenerationMixRequest(
                loadForecastRun,
                predictionSignatureService.buildGenerationMixScenarioSignature(loadForecastRun.getId(), reservoirKey),
                reservoirKey
        ));
        run.setModelVersion(modelVersionService.resolveCurrent(ModelVersionService.MODULE_GENERATION_MIX, "generation_mix_ai"));
        run.setForecastTimestamp(loadRequest.getTimestamp());
        run.setEstimatedDailyDemandMwh(estimatedLoadDemand);
        run.setReservoirPct(reservoirKey);
        run.setSource("python_model");
        run.setRunReason("MANUAL_RERUN_UPDATE");
        run.setReused(false);
        populateGenerationMixColumns(run, result);
        return generationMixRunRepository.save(run);
    }

    private GenerationMixRequestEntity createGenerationMixRequest(LoadForecastRun loadForecastRun,
                                                                  String scenarioSignature,
                                                                  Double reservoirPct) {
        GenerationMixRequestEntity entity = new GenerationMixRequestEntity();
        entity.setLoadForecastRun(loadForecastRun);
        entity.setScenarioSignature(scenarioSignature);
        entity.setReservoirPct(reservoirPct);
        try {
            return generationMixRequestRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            return generationMixRequestRepository.findByScenarioSignature(scenarioSignature).orElseThrow(() -> e);
        }
    }

    private Map<String, Object> buildPayloadFromExistingRun(GenerationMixRun run, boolean reused) {
        Map<String, Object> out = buildPayloadFromRun(run);
        out.put("generation_mix_run_id", run.getId());
        out.put("runId", run.getId());
        out.put("reused", reused);
        out.put("source", run.getSource());
        out.put("modelVersion", run.getModelVersion() != null ? run.getModelVersion().getVersionLabel() : null);
        out.put("modelName", run.getModelVersion() != null ? run.getModelVersion().getModelName() : null);
        return out;
    }

    private Map<String, Object> buildPayloadFromRun(GenerationMixRun run) {
        Map<String, Object> prediction = new LinkedHashMap<>();
        prediction.put("major_hydro", safeDouble(run.getMajorHydroMwh()));
        prediction.put("total_coal", safeDouble(run.getTotalCoalMwh()));
        prediction.put("total_thermal", safeDouble(run.getTotalThermalMwh()));
        prediction.put("wind", safeDouble(run.getWindMwh()));
        prediction.put("solar", safeDouble(run.getSolarMwh()));
        prediction.put("mini_hydro", safeDouble(run.getMiniHydroMwh()));

        double total = run.getTotalMwh() != null ? run.getTotalMwh() : 0.0;
        if (total <= 0) {
            total = safeDouble(run.getMajorHydroMwh())
                    + safeDouble(run.getTotalCoalMwh())
                    + safeDouble(run.getTotalThermalMwh())
                    + safeDouble(run.getWindMwh())
                    + safeDouble(run.getSolarMwh())
                    + safeDouble(run.getMiniHydroMwh());
        }
        double denom = total > 0 ? total : 1.0;
        Map<String, Object> percentages = new LinkedHashMap<>();
        percentages.put("major_hydro", round((safeDouble(run.getMajorHydroMwh()) / denom) * 100));
        percentages.put("total_coal", round((safeDouble(run.getTotalCoalMwh()) / denom) * 100));
        percentages.put("total_thermal", round((safeDouble(run.getTotalThermalMwh()) / denom) * 100));
        percentages.put("wind", round((safeDouble(run.getWindMwh()) / denom) * 100));
        percentages.put("solar", round((safeDouble(run.getSolarMwh()) / denom) * 100));
        percentages.put("mini_hydro", round((safeDouble(run.getMiniHydroMwh()) / denom) * 100));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("prediction_id", run.getLoadRequestId());
        out.put("forecast_timestamp", run.getForecastTimestamp() != null ? run.getForecastTimestamp().toString() : null);
        out.put("forecast_load", run.getLoadRequest() != null ? round(run.getLoadRequest().getPredictedLoad()) : null);
        out.put("estimated_load_demand", run.getEstimatedDailyDemandMwh());
        out.put("reservoir_pct", run.getReservoirPct());
        out.put("prediction", prediction);
        out.put("percentages", percentages);
        out.put("inputs_used", Map.of(
                "wind_lag1", safeDouble(run.getWindMwh()),
                "solar_lag1", safeDouble(run.getSolarMwh()),
                "mini_hydro_lag1", safeDouble(run.getMiniHydroMwh())
        ));
        out.put("total_mwh", total);
        return out;
    }

    private double safeDouble(Double value) {
        if (value != null && Double.isFinite(value)) {
            return value;
        }
        return 0.0;
    }

    private Map<String, Object> generateMixResult(LoadRequest loadRequest,
                                                  double reservoirPct,
                                                  double estimatedLoadDemand) {
        Map<String, Object> apiRequest = new LinkedHashMap<>();
        apiRequest.put("date", loadRequest.getTimestamp().toLocalDate().toString());
        apiRequest.put("reservoir_pct", round(reservoirPct));
        apiRequest.put("load_demand", estimatedLoadDemand);

        try {
            ResponseEntity<Map> responseEntity =
                    restTemplate.postForEntity(generationMixServiceUrl, apiRequest, Map.class);
            Map response = responseEntity.getBody();

            if (response == null || !response.containsKey("prediction") || !response.containsKey("percentages")) {
                throw new RuntimeException("Invalid response from Generation Mix AI");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("prediction_id", loadRequest.getId());
            result.put("forecast_timestamp", loadRequest.getTimestamp().toString());
            result.put("forecast_load", round(loadRequest.getPredictedLoad()));
            result.put("estimated_load_demand", estimatedLoadDemand);
            result.put("reservoir_pct", round(reservoirPct));
            result.putAll(response);
            return result;
        } catch (HttpStatusCodeException e) {
            throw new IllegalArgumentException(extractErrorMessage(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw new RuntimeException("Generation mix service is unavailable.");
        }
    }

    public double estimateDailyDemandMwh(LoadRequest loadRequest) {
        if (loadRequest.getPredictedLoad() <= 0) {
            throw new IllegalArgumentException("Forecast load must be available before generating the mix.");
        }

        try {
            String date = loadRequest.getTimestamp().toLocalDate().toString();
            double totalKw = 0.0;

            for (int hour = 0; hour < 24; hour++) {
                LoadRequest hourlyRequest = new LoadRequest();
                hourlyRequest.setTimestamp(LocalDateTime.parse(date + "T" + String.format("%02d", hour) + ":00:00"));
                hourlyRequest.setTemperature(loadRequest.getTemperature());
                hourlyRequest.setHumidity(loadRequest.getHumidity());
                hourlyRequest.setPublicEvent(loadRequest.getPublicEvent());

                double hourlyKw = loadService.predictValue(hourlyRequest);
                totalKw += hourlyKw;
            }

            return round(totalKw);
        } catch (Exception e) {
            return round(loadRequest.getPredictedLoad() * 24.0);
        }
    }

    private double resolveEstimatedDailyDemand(LoadRequest loadRequest, LoadForecastRun loadForecastRun) {
        Double cachedDemand = estimatedDailyDemandCache.get(loadForecastRun.getId());
        if (cachedDemand == null) {
            cachedDemand = loadForecastRun.getEstimatedDailyDemandMwh();
        }
        if (cachedDemand == null || cachedDemand <= 0) {
            cachedDemand = estimateDailyDemandMwh(loadRequest);
            loadForecastRun.setEstimatedDailyDemandMwh(cachedDemand);
            loadForecastRun.setDailyDemandMethod("24_hour_loop");
            loadForecastRunRepository.save(loadForecastRun);
        }
        estimatedDailyDemandCache.put(loadForecastRun.getId(), cachedDemand);
        loadRequest.setEstimatedDailyDemandMwh(cachedDemand);
        return cachedDemand;
    }

    private GenerationMixRun saveRun(LoadRequest loadRequest,
                                     LoadForecastRun loadForecastRun,
                                     GenerationMixRequestEntity requestEntity,
                                     ModelVersion modelVersion,
                                     double reservoirPct,
                                     double estimatedLoadDemand,
                                     Map<String, Object> result,
                                     boolean forceNew) {
        GenerationMixRun run = new GenerationMixRun();
        run.setLoadRequest(loadRequest);
        run.setGenerationMixRequest(requestEntity);
        run.setModelVersion(modelVersion);
        run.setForecastTimestamp(loadRequest.getTimestamp());
        run.setEstimatedDailyDemandMwh(((Number) result.getOrDefault("estimated_load_demand", estimatedLoadDemand)).doubleValue());
        run.setReservoirPct(round(reservoirPct));
        run.setSource("python_model");
        run.setRunReason(forceNew ? "FORCED_RERUN" : "NEW_SCENARIO");
        run.setReused(false);
        populateGenerationMixColumns(run, result);
        return generationMixRunRepository.save(run);
    }

    private void populateGenerationMixColumns(GenerationMixRun run, Map<String, Object> result) {
        Object predictionObj = result.get("prediction");
        if (predictionObj instanceof Map<?, ?> prediction) {
            run.setMajorHydroMwh(getNumber(prediction.get("major_hydro")));
            run.setTotalCoalMwh(getNumber(prediction.get("total_coal")));
            run.setTotalThermalMwh(getNumber(prediction.get("total_thermal")));
            run.setWindMwh(getNumber(prediction.get("wind")));
            run.setSolarMwh(getNumber(prediction.get("solar")));
            run.setMiniHydroMwh(getNumber(prediction.get("mini_hydro")));
            run.setTotalMwh(
                    getNumber(prediction.get("major_hydro"))
                            + getNumber(prediction.get("total_coal"))
                            + getNumber(prediction.get("total_thermal"))
                            + getNumber(prediction.get("wind"))
                            + getNumber(prediction.get("solar"))
                            + getNumber(prediction.get("mini_hydro"))
            );
        }
    }

    private double getNumber(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Generation mix request failed.";
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("error")) {
                return root.get("error").asText();
            }
        } catch (Exception ignored) {
        }

        return responseBody;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
