package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.*;
import com.example.loadforcasting.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class LegacyDataBackfillService implements ApplicationRunner {

    @Autowired
    private WeatherPredictionRepository weatherPredictionRepository;

    @Autowired
    private WeatherForecastRepository weatherForecastRepository;

    @Autowired
    private WeatherForecastRunRepository weatherForecastRunRepository;

    @Autowired
    private LoadRepository loadRepository;

    @Autowired
    private LoadForecastRepository loadForecastRepository;

    @Autowired
    private LoadForecastRunRepository loadForecastRunRepository;

    @Autowired
    private AnomalyRepository anomalyRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private GenerationMixRunRepository generationMixRunRepository;

    @Autowired
    private GenerationMixRequestRepository generationMixRequestRepository;

    @Autowired
    private CostPredictionRunRepository costPredictionRunRepository;

    @Autowired
    private CostPredictionRequestRepository costPredictionRequestRepository;

    @Autowired
    private PredictionSignatureService predictionSignatureService;

    @Autowired
    private ModelVersionService modelVersionService;

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        backfillWeather();
        backfillLoad();
        backfillAnomalies();
        backfillFeedback();
        backfillGenerationMix();
        backfillCostPredictions();
    }

    private void backfillWeather() {
        ModelVersion legacyVersion = modelVersionService.resolveLegacy(ModelVersionService.MODULE_WEATHER, "weather_ai_predictor");
        for (WeatherPrediction prediction : weatherPredictionRepository.findAll()) {
            if (prediction.getWeatherForecastRunId() != null) {
                continue;
            }

            LocalDateTime forecastTimestamp = prediction.getForecastTimestamp();
            if (forecastTimestamp == null && prediction.getPredictionDate() != null && prediction.getPredictionTime() != null) {
                forecastTimestamp = LocalDateTime.of(prediction.getPredictionDate(), prediction.getPredictionTime());
            }
            if (forecastTimestamp == null) {
                continue;
            }

            LocalDateTime resolvedForecastTimestamp = forecastTimestamp;
            WeatherForecast forecast = weatherForecastRepository.findByForecastTimestamp(resolvedForecastTimestamp)
                    .orElseGet(() -> {
                        WeatherForecast entity = new WeatherForecast();
                        entity.setForecastTimestamp(resolvedForecastTimestamp);
                        entity.setCurrentStatus("ACTIVE");
                        return weatherForecastRepository.save(entity);
                    });

            WeatherForecastRun run = weatherForecastRunRepository
                    .findFirstByWeatherForecastAndModelVersionOrderByCreatedAtDesc(forecast, legacyVersion)
                    .orElseGet(() -> {
                        WeatherForecastRun entity = new WeatherForecastRun();
                        entity.setWeatherForecast(forecast);
                        entity.setModelVersion(legacyVersion);
                        entity.setTemperature(prediction.getTemperature());
                        entity.setHumidity(prediction.getHumidity());
                        entity.setWindSpeed(prediction.getWindSpeed());
                        entity.setRainfall(prediction.getRainfall());
                        entity.setSolarIrradiance(prediction.getSolarIrradiance());
                        entity.setSource(prediction.getSource() != null ? prediction.getSource() : "legacy");
                        entity.setRunReason("LEGACY_IMPORT");
                        entity.setReused(false);
                        return weatherForecastRunRepository.save(entity);
                    });

            prediction.setForecastTimestamp(forecastTimestamp);
            prediction.setWeatherForecastId(forecast.getId());
            prediction.setWeatherForecastRunId(run.getId());
            if (prediction.getSource() == null) {
                prediction.setSource(run.getSource());
            }
            if (prediction.getModelVersionLabel() == null) {
                prediction.setModelVersionLabel(legacyVersion.getVersionLabel());
            }
            weatherPredictionRepository.save(prediction);
        }
    }

    private void backfillLoad() {
        ModelVersion legacyVersion = modelVersionService.resolveLegacy(ModelVersionService.MODULE_LOAD, "load_ai_predictor");
        for (LoadRequest request : loadRepository.findAll()) {
            if (request.getLoadForecastRunId() != null || request.getTimestamp() == null) {
                continue;
            }

            String signature = predictionSignatureService.buildLoadSignature(
                    request.getTimestamp(),
                    request.getTemperature(),
                    request.getHumidity(),
                    request.getPublicEvent()
            );
            LoadForecast forecast = loadForecastRepository.findByInputSignature(signature)
                    .orElseGet(() -> {
                        LoadForecast entity = new LoadForecast();
                        entity.setInputSignature(signature);
                        entity.setForecastTimestamp(request.getTimestamp());
                        entity.setTemperatureInput(request.getTemperature());
                        entity.setHumidityInput(request.getHumidity());
                        entity.setPublicEventFlag(request.getPublicEvent());
                        return loadForecastRepository.save(entity);
                    });

            LoadForecastRun run = loadForecastRunRepository
                    .findFirstByLoadForecastAndModelVersionOrderByCreatedAtDesc(forecast, legacyVersion)
                    .orElseGet(() -> {
                        LoadForecastRun entity = new LoadForecastRun();
                        entity.setLoadForecast(forecast);
                        entity.setModelVersion(legacyVersion);
                        entity.setPredictedLoadKw(request.getPredictedLoad());
                        entity.setEstimatedDailyDemandMwh(
                                request.getEstimatedDailyDemandMwh() != null
                                        ? request.getEstimatedDailyDemandMwh()
                                        : Math.round(request.getPredictedLoad() * 24.0 * 100.0) / 100.0
                        );
                        entity.setDailyDemandMethod("legacy_import");
                        entity.setSource(request.getSource() != null ? request.getSource() : "legacy");
                        entity.setRunReason("LEGACY_IMPORT");
                        entity.setReused(Boolean.TRUE.equals(request.getRunReused()));
                        return loadForecastRunRepository.save(entity);
                    });

            request.setInputSignature(signature);
            request.setLoadForecastId(forecast.getId());
            request.setLoadForecastRunId(run.getId());
            if (request.getModelVersionLabel() == null) {
                request.setModelVersionLabel(legacyVersion.getVersionLabel());
            }
            if (request.getSource() == null) {
                request.setSource(run.getSource());
            }
            if (request.getEstimatedDailyDemandMwh() == null) {
                request.setEstimatedDailyDemandMwh(run.getEstimatedDailyDemandMwh());
            }
            loadRepository.save(request);
        }
    }

    private void backfillAnomalies() {
        ModelVersion legacyVersion = modelVersionService.resolveLegacy(ModelVersionService.MODULE_ANOMALY, "anomaly_detector");
        for (Anomaly anomaly : anomalyRepository.findAll()) {
            if (anomaly.getModelVersion() == null) {
                anomaly.setModelVersion(legacyVersion);
            }
            if (anomaly.getLoadForecastRun() == null && anomaly.getPredictionId() != null) {
                loadRepository.findById(anomaly.getPredictionId())
                        .map(LoadRequest::getLoadForecastRunId)
                        .flatMap(loadForecastRunRepository::findById)
                        .ifPresent(anomaly::setLoadForecastRun);
            }
            if (anomaly.getSource() == null) {
                anomaly.setSource("legacy");
            }
            if (anomaly.getModelName() == null) {
                anomaly.setModelName("legacy_detector");
            }
            anomalyRepository.save(anomaly);
        }
    }

    private void backfillFeedback() {
        for (Feedback feedback : feedbackRepository.findAll()) {
            if (feedback.getSubmittedByUser() == null && feedback.getUserEmail() != null) {
                userRepository.findByEmail(feedback.getUserEmail()).ifPresent(feedback::setSubmittedByUser);
            }
            if (feedback.getContactEmailSnapshot() == null) {
                feedback.setContactEmailSnapshot(feedback.getUserEmail());
            }
            if (feedback.getLoadForecastRun() == null && feedback.getPredictionId() != null) {
                loadRepository.findById(feedback.getPredictionId())
                        .map(LoadRequest::getLoadForecastRunId)
                        .flatMap(loadForecastRunRepository::findById)
                        .ifPresent(feedback::setLoadForecastRun);
            }
            if (feedback.getSubjectScope() == null) {
                if (feedback.getAnomaly() != null) {
                    feedback.setSubjectScope("ANOMALY");
                } else if (feedback.getLoadForecastRun() != null) {
                    feedback.setSubjectScope("LOAD_FORECAST");
                } else {
                    feedback.setSubjectScope("GENERAL");
                }
            }
            feedbackRepository.save(feedback);
        }
    }

    private void backfillGenerationMix() {
        ModelVersion legacyVersion = modelVersionService.resolveLegacy(ModelVersionService.MODULE_GENERATION_MIX, "generation_mix_ai");
        for (GenerationMixRun run : generationMixRunRepository.findAll()) {
            if (run.getGenerationMixRequest() == null && run.getLoadRequest() != null && run.getLoadRequest().getLoadForecastRunId() != null) {
                loadForecastRunRepository.findById(run.getLoadRequest().getLoadForecastRunId()).ifPresent(loadForecastRun -> {
                    String signature = predictionSignatureService.buildGenerationMixScenarioSignature(
                            loadForecastRun.getId(),
                            run.getReservoirPct() != null ? run.getReservoirPct() : 0.0
                    );
                    GenerationMixRequestEntity requestEntity = generationMixRequestRepository.findByScenarioSignature(signature)
                            .orElseGet(() -> {
                                GenerationMixRequestEntity entity = new GenerationMixRequestEntity();
                                entity.setScenarioSignature(signature);
                                entity.setLoadForecastRun(loadForecastRun);
                                entity.setReservoirPct(run.getReservoirPct() != null ? run.getReservoirPct() : 0.0);
                                try {
                                    return generationMixRequestRepository.save(entity);
                                } catch (DataIntegrityViolationException e) {
                                    return generationMixRequestRepository.findByScenarioSignature(signature).orElseThrow(() -> e);
                                }
                            });
                    run.setGenerationMixRequest(requestEntity);
                });
            }
            if (run.getModelVersion() == null) {
                run.setModelVersion(legacyVersion);
            }
            if (run.getSource() == null) {
                run.setSource("legacy");
            }
            if (run.getRunReason() == null) {
                run.setRunReason("LEGACY_IMPORT");
            }
            if (run.getReused() == null) {
                run.setReused(false);
            }
            if (run.getTotalMwh() == null) {
                run.setTotalMwh(
                        safe(run.getMajorHydroMwh())
                                + safe(run.getTotalCoalMwh())
                                + safe(run.getTotalThermalMwh())
                                + safe(run.getWindMwh())
                                + safe(run.getSolarMwh())
                                + safe(run.getMiniHydroMwh())
                );
            }
            generationMixRunRepository.save(run);
        }
    }

    private void backfillCostPredictions() {
        ModelVersion legacyVersion = modelVersionService.resolveLegacy(ModelVersionService.MODULE_COST, "cost_prediction_ai");
        for (CostPredictionRun run : costPredictionRunRepository.findAll()) {
            if (run.getCostPredictionRequest() == null && run.getGenerationMixRun() != null && run.getGenerationMixRun().getId() != null) {
                String signature = predictionSignatureService.buildCostScenarioSignature(
                        run.getGenerationMixRun().getId(),
                        safe(run.getFoPrice()),
                        safe(run.getCoalPrice()),
                        safe(run.getDieselPrice()),
                        safe(run.getNaphthaPrice())
                );
                CostPredictionRequestEntity requestEntity = costPredictionRequestRepository.findByScenarioSignature(signature)
                        .orElseGet(() -> {
                            CostPredictionRequestEntity entity = new CostPredictionRequestEntity();
                            entity.setScenarioSignature(signature);
                            entity.setGenerationMixRun(run.getGenerationMixRun());
                            entity.setFoPrice(safe(run.getFoPrice()));
                            entity.setCoalPrice(safe(run.getCoalPrice()));
                            entity.setDieselPrice(safe(run.getDieselPrice()));
                            entity.setNaphthaPrice(safe(run.getNaphthaPrice()));
                            try {
                                return costPredictionRequestRepository.save(entity);
                            } catch (DataIntegrityViolationException e) {
                                return costPredictionRequestRepository.findByScenarioSignature(signature).orElseThrow(() -> e);
                            }
                        });
                run.setCostPredictionRequest(requestEntity);
            }
            if (run.getModelVersion() == null) {
                run.setModelVersion(legacyVersion);
            }
            if (run.getSource() == null) {
                run.setSource("legacy");
            }
            if (run.getRunReason() == null) {
                run.setRunReason("LEGACY_IMPORT");
            }
            if (run.getReused() == null) {
                run.setReused(false);
            }
            costPredictionRunRepository.save(run);
        }
    }

    private double safe(Double value) {
        return value != null ? value : 0.0;
    }
}
