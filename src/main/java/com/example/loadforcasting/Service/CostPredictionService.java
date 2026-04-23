package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.CostPredictionRequestEntity;
import com.example.loadforcasting.Entity.CostPredictionRun;
import com.example.loadforcasting.Entity.GenerationMixRun;
import com.example.loadforcasting.Entity.ModelVersion;
import com.example.loadforcasting.Repository.CostPredictionRequestRepository;
import com.example.loadforcasting.Repository.CostPredictionRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CostPredictionService {

    @Value("${cost.service.url:http://localhost:5004/predict}")
    private String costServiceUrl;

    @Autowired
    private CostPredictionRunRepository costPredictionRunRepository;

    @Autowired
    private CostPredictionRequestRepository costPredictionRequestRepository;

    @Autowired
    private PredictionSignatureService predictionSignatureService;

    @Autowired
    private ModelVersionService modelVersionService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> predictCost(GenerationMixRun run,
                                           double foPrice,
                                           double coalPrice,
                                           double dieselPrice,
                                           double naphthaPrice) {
        return predictCost(run, foPrice, coalPrice, dieselPrice, naphthaPrice, false);
    }

    public Map<String, Object> predictCost(GenerationMixRun run,
                                           double foPrice,
                                           double coalPrice,
                                           double dieselPrice,
                                           double naphthaPrice,
                                           boolean forceNew) {
        if (run == null || run.getId() == null) {
            throw new IllegalArgumentException("Saved generation mix run is required.");
        }

        Double foKey = normalizePrice(foPrice);
        Double coalKey = normalizePrice(coalPrice);
        Double dieselKey = normalizePrice(dieselPrice);
        Double naphthaKey = normalizePrice(naphthaPrice);

        String scenarioSignature = predictionSignatureService.buildCostScenarioSignature(
                run.getId(), foKey, coalKey, dieselKey, naphthaKey
        );
        CostPredictionRequestEntity requestEntity = costPredictionRequestRepository.findByScenarioSignature(scenarioSignature)
                .orElseGet(() -> createRequest(run, foKey, coalKey, dieselKey, naphthaKey, scenarioSignature));

        ModelVersion modelVersion = modelVersionService.resolveCurrent(
                ModelVersionService.MODULE_COST,
                "cost_prediction_ai"
        );

        var existing = costPredictionRunRepository
                .findFirstByCostPredictionRequestAndModelVersionOrderByCreatedAtDesc(requestEntity, modelVersion);

        if (existing.isPresent() && !forceNew) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("unit_cost", existing.get().getUnitCost());
            out.put("cost_run_id", existing.get().getId());
            out.put("runId", existing.get().getId());
            out.put("reused", true);
            out.put("info", "Already predicted for this mix and fuel prices. Showing the saved result.");
            out.put("source", existing.get().getSource());
            out.put("modelVersion", modelVersion.getVersionLabel());
            out.put("modelName", modelVersion.getModelName());
            return out;
        }

        Map<String, Object> body = executePrediction(run, foKey, coalKey, dieselKey, naphthaKey);
        CostPredictionRun savedRun;
        try {
            savedRun = saveRun(run, requestEntity, modelVersion, foKey, coalKey, dieselKey, naphthaKey, body, forceNew);
        } catch (DataIntegrityViolationException e) {
            var fallback = costPredictionRunRepository
                    .findFirstByGenerationMixRun_IdAndFoPriceAndCoalPriceAndDieselPriceAndNaphthaPriceOrderByCreatedAtDesc(
                            run.getId(), foKey, coalKey, dieselKey, naphthaKey
                    )
                    .orElse(null);
            if (fallback != null) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("unit_cost", fallback.getUnitCost());
                out.put("cost_run_id", fallback.getId());
                out.put("runId", fallback.getId());
                out.put("reused", true);
                out.put("info", "Already predicted for this mix and fuel prices. Showing the saved result.");
                out.put("source", fallback.getSource());
                out.put("modelVersion", modelVersion.getVersionLabel());
                out.put("modelName", modelVersion.getModelName());
                return out;
            }
            throw e;
        }
        body.put("cost_run_id", savedRun.getId());
        body.put("runId", savedRun.getId());
        body.put("reused", false);
        body.put("source", savedRun.getSource());
        body.put("modelVersion", modelVersion.getVersionLabel());
        body.put("modelName", modelVersion.getModelName());
        return body;
    }

    public Map<String, Object> updateCostRun(CostPredictionRun existingRun,
                                             GenerationMixRun mixRun,
                                             double foPrice,
                                             double coalPrice,
                                             double dieselPrice,
                                             double naphthaPrice) {
        if (existingRun == null) {
            throw new IllegalArgumentException("Saved cost prediction run not found.");
        }

        Double foKey = normalizePrice(foPrice);
        Double coalKey = normalizePrice(coalPrice);
        Double dieselKey = normalizePrice(dieselPrice);
        Double naphthaKey = normalizePrice(naphthaPrice);
        Map<String, Object> result = executePrediction(mixRun, foKey, coalKey, dieselKey, naphthaKey);

        existingRun.setGenerationMixRun(mixRun);
        existingRun.setCostPredictionRequest(createRequest(
                mixRun,
                foKey,
                coalKey,
                dieselKey,
                naphthaKey,
                predictionSignatureService.buildCostScenarioSignature(mixRun.getId(), foKey, coalKey, dieselKey, naphthaKey)
        ));
        existingRun.setModelVersion(modelVersionService.resolveCurrent(ModelVersionService.MODULE_COST, "cost_prediction_ai"));
        existingRun.setForecastTimestamp(mixRun.getForecastTimestamp());
        existingRun.setFoPrice(foKey);
        existingRun.setCoalPrice(coalKey);
        existingRun.setDieselPrice(dieselKey);
        existingRun.setNaphthaPrice(naphthaKey);
        existingRun.setUnitCost(getUnitCost(result));
        existingRun.setSource("python_model");
        existingRun.setRunReason("MANUAL_RERUN_UPDATE");
        existingRun.setReused(false);

        costPredictionRunRepository.save(existingRun);
        result.put("cost_run_id", existingRun.getId());
        result.put("runId", existingRun.getId());
        result.put("source", existingRun.getSource());
        result.put("modelVersion", existingRun.getModelVersion() != null ? existingRun.getModelVersion().getVersionLabel() : null);
        result.put("modelName", existingRun.getModelVersion() != null ? existingRun.getModelVersion().getModelName() : null);
        return result;
    }

    private CostPredictionRequestEntity createRequest(GenerationMixRun mixRun,
                                                      Double foPrice,
                                                      Double coalPrice,
                                                      Double dieselPrice,
                                                      Double naphthaPrice,
                                                      String scenarioSignature) {
        CostPredictionRequestEntity requestEntity = new CostPredictionRequestEntity();
        requestEntity.setScenarioSignature(scenarioSignature);
        requestEntity.setGenerationMixRun(mixRun);
        requestEntity.setFoPrice(foPrice);
        requestEntity.setCoalPrice(coalPrice);
        requestEntity.setDieselPrice(dieselPrice);
        requestEntity.setNaphthaPrice(naphthaPrice);
        try {
            return costPredictionRequestRepository.save(requestEntity);
        } catch (DataIntegrityViolationException e) {
            return costPredictionRequestRepository.findByScenarioSignature(scenarioSignature).orElseThrow(() -> e);
        }
    }

    private Map<String, Object> executePrediction(GenerationMixRun run,
                                                  double foPrice,
                                                  double coalPrice,
                                                  double dieselPrice,
                                                  double naphthaPrice) {
        if (run == null) {
            throw new IllegalArgumentException("Saved generation mix run is required.");
        }
        if (run.getForecastTimestamp() == null) {
            throw new IllegalArgumentException("Saved run is missing forecast timestamp.");
        }

        String trainingRangeWarning = null;
        LocalDate forecastDate = run.getForecastTimestamp().toLocalDate();
        int forecastYear = forecastDate.getYear();
        if (forecastYear < 2020 || forecastYear >= 2027) {
            trainingRangeWarning = "Forecast date is outside the supported range. Prediction may be less accurate.";
        }

        validateFuelPrices(foPrice, coalPrice, dieselPrice, naphthaPrice);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("date", run.getForecastTimestamp().toLocalDate().toString());
        request.put("load_demand", run.getEstimatedDailyDemandMwh());
        request.put("major_hydro", safeNumber(run.getMajorHydroMwh()));
        request.put("total_coal", safeNumber(run.getTotalCoalMwh()));
        request.put("total_thermal", safeNumber(run.getTotalThermalMwh()));
        request.put("wind", safeNumber(run.getWindMwh()));
        request.put("solar", safeNumber(run.getSolarMwh()));
        request.put("mini_hydro", safeNumber(run.getMiniHydroMwh()));
        request.put("fo_price", foPrice);
        request.put("coal_price", coalPrice);
        request.put("diesel_price", dieselPrice);
        request.put("naphtha_price", naphthaPrice);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    costServiceUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Invalid response from Cost Prediction AI");
            }

            Map<String, Object> out = objectMapper.convertValue(body, new TypeReference<Map<String, Object>>() {});
            if (trainingRangeWarning != null) {
                out.put("trainingWarning", trainingRangeWarning);
            }
            return out;
        } catch (HttpStatusCodeException e) {
            throw new IllegalArgumentException(extractErrorMessage(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw new RuntimeException("Cost prediction service is unavailable.");
        }
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Cost prediction failed.";
        }
        try {
            Map<?, ?> parsed = objectMapper.readValue(responseBody, Map.class);
            Object error = parsed.get("error");
            if (error != null) {
                String msg = String.valueOf(error).trim();
                if (!msg.isBlank()) {
                    return msg;
                }
            }
        } catch (Exception ignore) {
        }
        return responseBody;
    }

    public void deleteCostRun(Long id) {
        if (id == null || !costPredictionRunRepository.existsById(id)) {
            throw new IllegalArgumentException("Saved cost prediction run not found.");
        }
        costPredictionRunRepository.deleteById(id);
    }

    private CostPredictionRun saveRun(GenerationMixRun mixRun,
                                      CostPredictionRequestEntity requestEntity,
                                      ModelVersion modelVersion,
                                      double foPrice,
                                      double coalPrice,
                                      double dieselPrice,
                                      double naphthaPrice,
                                      Map<String, Object> result,
                                      boolean forceNew) {
        CostPredictionRun run = new CostPredictionRun();
        run.setGenerationMixRun(mixRun);
        run.setCostPredictionRequest(requestEntity);
        run.setModelVersion(modelVersion);
        run.setForecastTimestamp(mixRun.getForecastTimestamp());
        run.setFoPrice(foPrice);
        run.setCoalPrice(coalPrice);
        run.setDieselPrice(dieselPrice);
        run.setNaphthaPrice(naphthaPrice);
        run.setUnitCost(getUnitCost(result));
        run.setSource("python_model");
        run.setRunReason(forceNew ? "FORCED_RERUN" : "NEW_SCENARIO");
        run.setReused(false);
        return costPredictionRunRepository.save(run);
    }

    private double getUnitCost(Map<String, Object> result) {
        Object unitCostObj = result.get("unit_cost");
        if (unitCostObj instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("Cost prediction response is missing unit_cost.");
    }

    private Double normalizePrice(double value) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private void validateFuelPrices(double foPrice,
                                    double coalPrice,
                                    double dieselPrice,
                                    double naphthaPrice) {
        validatePrice("FO Price", foPrice);
        validatePrice("Coal Price", coalPrice);
        validatePrice("Diesel Price", dieselPrice);
        validatePrice("Naphtha Price", naphthaPrice);
    }

    private void validatePrice(String label, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be a valid number.");
        }
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be greater than 0.");
        }
        if (value > 2000) {
            throw new IllegalArgumentException(label + " must not exceed 2000.");
        }
    }

    private double safeNumber(Double value) {
        if (value != null && Double.isFinite(value)) {
            return value;
        }
        return 0.0;
    }
}
