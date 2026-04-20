package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.ModelVersion;
import com.example.loadforcasting.Entity.WeatherForecast;
import com.example.loadforcasting.Entity.WeatherForecastRun;
import com.example.loadforcasting.Entity.WeatherPrediction;
import com.example.loadforcasting.Repository.WeatherForecastRepository;
import com.example.loadforcasting.Repository.WeatherForecastRunRepository;
import com.example.loadforcasting.Repository.WeatherPredictionRepository;
import com.example.loadforcasting.dto.WeatherPredictionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WeatherPredictionService {

    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter SECOND_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private WeatherPredictionRepository repository;

    @Autowired
    private WeatherForecastRepository weatherForecastRepository;

    @Autowired
    private WeatherForecastRunRepository weatherForecastRunRepository;

    @Autowired
    private ModelVersionService modelVersionService;

    @SuppressWarnings("FieldCanBeLocal")
    private String PYTHON_API_URL = "http://localhost:5000/predict";

    public WeatherPredictionResult predictTransient(String dateTime) {
        try {
            LocalDateTime predictionDateTime = parseDateTime(dateTime);
            String pythonRequestTimestamp = normalizePythonRequestTimestamp(dateTime, predictionDateTime);
            Map<String, Object> predictions = callPythonModel(pythonRequestTimestamp);

            return new WeatherPredictionResult(
                    predictionDateTime,
                    asDouble(predictions.get("temperature")),
                    asDouble(predictions.get("humidity")),
                    asDouble(predictions.get("wind_speed")),
                    asDouble(predictions.get("rainfall")),
                    asDouble(predictions.get("solar_irradiance")),
                    String.valueOf(predictions.getOrDefault("source", "python_model"))
            );
        } catch (Exception e) {
            throw new RuntimeException("Error predicting weather: " + e.getMessage(), e);
        }
    }

    @Transactional
    public WeatherPrediction predictAndSave(String dateTime) {
        return predictAndSave(dateTime, false);
    }

    @Transactional
    public WeatherPrediction predictAndSave(String dateTime, boolean forceNew) {
        WeatherPredictionResult result = predictTransient(dateTime);
        ModelVersion modelVersion = modelVersionService.resolveCurrent(
                ModelVersionService.MODULE_WEATHER,
                "weather_ai_predictor"
        );

        WeatherForecast weatherForecast = weatherForecastRepository.findByForecastTimestamp(result.predictionDateTime())
                .orElseGet(() -> {
                    WeatherForecast entity = new WeatherForecast();
                    entity.setForecastTimestamp(result.predictionDateTime());
                    entity.setCurrentStatus("ACTIVE");
                    return weatherForecastRepository.save(entity);
                });

        Optional<WeatherForecastRun> existingRun = weatherForecastRunRepository
                .findFirstByWeatherForecastAndModelVersionOrderByCreatedAtDesc(weatherForecast, modelVersion);

        WeatherForecastRun run;
        if (existingRun.isPresent() && !forceNew) {
            run = existingRun.get();
            WeatherPrediction existingPrediction = repository.findFirstByWeatherForecastRunIdOrderByCreatedAtDesc(run.getId());
            if (existingPrediction != null) {
                hydrateLegacyPrediction(existingPrediction, result, weatherForecast, run, modelVersion, true);
                return repository.save(existingPrediction);
            }
        }

        run = new WeatherForecastRun();
        run.setWeatherForecast(weatherForecast);
        run.setModelVersion(modelVersion);
        run.setTemperature(result.temperature());
        run.setHumidity(result.humidity());
        run.setWindSpeed(result.windSpeed());
        run.setRainfall(result.rainfall());
        run.setSolarIrradiance(result.solarIrradiance());
        run.setSource(result.source());
        run.setRunReason(forceNew ? "FORCED_RERUN" : "NEW_TIMESTAMP");
        run.setReused(false);
        run = weatherForecastRunRepository.save(run);

        WeatherPrediction prediction = new WeatherPrediction();
        hydrateLegacyPrediction(prediction, result, weatherForecast, run, modelVersion, false);
        return repository.save(prediction);
    }

    @Transactional
    public WeatherPrediction updatePrediction(Long id, String dateTime) {
        WeatherPrediction prediction = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prediction with ID " + id + " not found"));
        WeatherPredictionResult result = predictTransient(dateTime);
        ModelVersion modelVersion = modelVersionService.resolveCurrent(
                ModelVersionService.MODULE_WEATHER,
                "weather_ai_predictor"
        );

        WeatherForecast weatherForecast = weatherForecastRepository.findByForecastTimestamp(result.predictionDateTime())
                .orElseGet(() -> {
                    WeatherForecast entity = new WeatherForecast();
                    entity.setForecastTimestamp(result.predictionDateTime());
                    entity.setCurrentStatus("ACTIVE");
                    return weatherForecastRepository.save(entity);
                });

        WeatherForecastRun run = new WeatherForecastRun();
        run.setWeatherForecast(weatherForecast);
        run.setModelVersion(modelVersion);
        run.setTemperature(result.temperature());
        run.setHumidity(result.humidity());
        run.setWindSpeed(result.windSpeed());
        run.setRainfall(result.rainfall());
        run.setSolarIrradiance(result.solarIrradiance());
        run.setSource(result.source());
        run.setRunReason("FORCED_RERUN");
        run.setReused(false);
        run = weatherForecastRunRepository.save(run);

        hydrateLegacyPrediction(prediction, result, weatherForecast, run, modelVersion, false);
        return repository.save(prediction);
    }

    public boolean deletePrediction(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Prediction with ID " + id + " not found");
        }
        repository.deleteById(id);
        return true;
    }

    public List<WeatherPrediction> getAllPredictions() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", repository.getTotalPredictions());
        stats.put("avgTemperature", repository.getAverageTemperature());
        stats.put("avgHumidity", repository.getAverageHumidity());
        stats.put("maxTemperature", repository.getMaxTemperature());
        stats.put("minTemperature", repository.getMinTemperature());
        stats.put("totalRainfall", repository.getTotalRainfall());
        stats.put("monthlyTemps", repository.getMonthlyTemperatureAverages());
        return stats;
    }

    private void hydrateLegacyPrediction(WeatherPrediction prediction,
                                         WeatherPredictionResult result,
                                         WeatherForecast weatherForecast,
                                         WeatherForecastRun run,
                                         ModelVersion modelVersion,
                                         boolean reused) {
        prediction.setPredictionDate(result.predictionDateTime().toLocalDate());
        prediction.setPredictionTime(result.predictionDateTime().toLocalTime());
        prediction.setTemperature(result.temperature());
        prediction.setHumidity(result.humidity());
        prediction.setWindSpeed(result.windSpeed());
        prediction.setRainfall(result.rainfall());
        prediction.setSolarIrradiance(result.solarIrradiance());
        prediction.setForecastTimestamp(result.predictionDateTime());
        prediction.setWeatherForecastId(weatherForecast.getId());
        prediction.setWeatherForecastRunId(run.getId());
        prediction.setSource(reused ? run.getSource() : result.source());
        prediction.setModelVersionLabel(modelVersion.getVersionLabel());
    }

    private LocalDateTime parseDateTime(String dateTime) {
        if (dateTime == null || dateTime.isBlank()) {
            throw new IllegalArgumentException("Timestamp is required");
        }

        try {
            return LocalDateTime.parse(dateTime, SECOND_FORMATTER);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(dateTime, MINUTE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid timestamp format. Use YYYY-MM-DDTHH:MM or YYYY-MM-DDTHH:MM:SS");
        }
    }

    private String normalizePythonRequestTimestamp(String originalDateTime, LocalDateTime parsedDateTime) {
        if (originalDateTime != null && originalDateTime.length() == 19) {
            return parsedDateTime.format(SECOND_FORMATTER);
        }
        return parsedDateTime.format(MINUTE_FORMATTER);
    }

    private Map<String, Object> callPythonModel(String dateTime) {
        try {
            Map<String, String> request = new HashMap<>();
            request.put("date_time", dateTime);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(
                    PYTHON_API_URL, HttpMethod.POST, entity, Map.class);

            if (response.getBody() != null && !response.getBody().containsKey("error")) {
                Map<String, Object> predictions = new HashMap<>();
                predictions.put("temperature", asDouble(response.getBody().get("temperature")));
                predictions.put("humidity", asDouble(response.getBody().get("humidity")));
                predictions.put("wind_speed", asDouble(response.getBody().get("wind_speed")));
                predictions.put("rainfall", asDouble(response.getBody().get("rainfall")));
                predictions.put("solar_irradiance", asDouble(response.getBody().get("solar_irradiance")));
                predictions.put("source", "python_model");
                return predictions;
            }

            return getFallbackPredictions();
        } catch (Exception e) {
            return getFallbackPredictions();
        }
    }

    private Map<String, Object> getFallbackPredictions() {
        Map<String, Object> predictions = new HashMap<>();

        double temp = 26.0 + (Math.random() * 8.0);
        double humidity = 65.0 + (Math.random() * 20.0);
        double wind = 1.5 + (Math.random() * 4.0);
        double solar = 400.0 + (Math.random() * 300.0);
        double rain = Math.random() > 0.7 ? (Math.random() * 12.0) : 0.0;

        predictions.put("temperature", temp);
        predictions.put("humidity", humidity);
        predictions.put("wind_speed", wind);
        predictions.put("rainfall", rain);
        predictions.put("solar_irradiance", solar);
        predictions.put("source", "fallback");

        return predictions;
    }

    private double asDouble(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Weather service returned an invalid numeric value");
        }
        return number.doubleValue();
    }
}
