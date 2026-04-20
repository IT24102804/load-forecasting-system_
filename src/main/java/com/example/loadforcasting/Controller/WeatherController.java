package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.PredictionRequest;
import com.example.loadforcasting.Entity.WeatherPrediction;
import com.example.loadforcasting.Service.WeatherPredictionService;
import com.example.loadforcasting.dto.WeatherPredictionResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WeatherController {

    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter SECOND_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private WeatherPredictionService service;

    @GetMapping("/weather")
    public String home(Model model) {
        model.addAttribute("predictionRequest", new PredictionRequest());
        model.addAttribute("history", service.getAllPredictions());
        model.addAttribute("statistics", service.getStatistics());
        return "weather/weather";
    }

    @PostMapping("/predict")
    public String predict(@Valid @ModelAttribute PredictionRequest request,
                          BindingResult bindingResult,
                          Model model) {

        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().get(0).getDefaultMessage();
            model.addAttribute("error", errorMsg);
            model.addAttribute("success", false);
            model.addAttribute("message", "Prediction failed: " + errorMsg);
            populateWeatherPage(model);
            return "weather/weather";
        }

        try {
            validateWeatherTimestamp(request.getDateTime(), false);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("success", false);
            model.addAttribute("message", "Prediction failed: " + e.getMessage());
            populateWeatherPage(model);
            return "weather/weather";
        }

        try {
            WeatherPrediction prediction = service.predictAndSave(request.getDateTime());
            model.addAttribute("prediction", prediction);
            model.addAttribute("success", true);
            model.addAttribute("message", "Weather prediction completed successfully.");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("success", false);
            model.addAttribute("message", "Failed to predict weather: " + e.getMessage());
        }

        populateWeatherPage(model);
        return "weather/weather";
    }

    @PostMapping("/api/weather/predict-transient")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> predictTransient(@RequestBody(required = false) Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String timestamp = request != null ? request.get("timestamp") : null;
            validateWeatherTimestamp(timestamp, true);

            WeatherPredictionResult prediction = service.predictTransient(timestamp);
            response.put("temperature", prediction.temperature());
            response.put("humidity", prediction.humidity());
            response.put("wind_speed", prediction.windSpeed());
            response.put("rainfall", prediction.rainfall());
            response.put("solar_irradiance", prediction.solarIrradiance());
            response.put("source", prediction.source());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("error", "Failed to predict transient weather: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @DeleteMapping("/api/predictions/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deletePrediction(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            service.deletePrediction(id);
            response.put("success", true);
            response.put("message", "Prediction deleted successfully!");
            response.put("history", service.getAllPredictions());
            response.put("statistics", service.getStatistics());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/api/predictions/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updatePrediction(@PathVariable Long id,
                                                                @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String dateTime = request.get("dateTime");
            WeatherPrediction updated = service.updatePrediction(id, dateTime);
            response.put("success", true);
            response.put("message", "Prediction updated successfully!");
            response.put("prediction", updated);
            response.put("history", service.getAllPredictions());
            response.put("statistics", service.getStatistics());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/api/predictions")
    @ResponseBody
    public List<WeatherPrediction> getPredictions() {
        return service.getAllPredictions();
    }

    @GetMapping("/api/statistics")
    @ResponseBody
    public Map<String, Object> getStatistics() {
        return service.getStatistics();
    }

    @GetMapping("/health")
    @ResponseBody
    public String health() {
        return "Weather Prediction System is running! SQL Server connected.";
    }

    private void populateWeatherPage(Model model) {
        model.addAttribute("predictionRequest", new PredictionRequest());
        model.addAttribute("history", service.getAllPredictions());
        model.addAttribute("statistics", service.getStatistics());
    }

    private void validateWeatherTimestamp(String timestamp, boolean allowSeconds) {
        if (timestamp == null || timestamp.isBlank()) {
            throw new IllegalArgumentException("Timestamp is required");
        }

        LocalDateTime inputDateTime = parseTimestamp(timestamp, allowSeconds);

        int year = inputDateTime.getYear();
        if (year < 2020 || year > 2030) {
            throw new IllegalArgumentException("Year must be between 2020 and 2030");
        }

        int month = inputDateTime.getMonthValue();
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }

        int day = inputDateTime.getDayOfMonth();
        int maxDay = inputDateTime.toLocalDate().lengthOfMonth();
        if (day < 1 || day > maxDay) {
            throw new IllegalArgumentException(String.format("Invalid day. Month %d has %d days", month, maxDay));
        }

        int hour = inputDateTime.getHour();
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Hour must be between 0 and 23");
        }

        int minute = inputDateTime.getMinute();
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Minute must be between 0 and 59");
        }
    }

    private LocalDateTime parseTimestamp(String timestamp, boolean allowSeconds) {
        try {
            if (allowSeconds) {
                try {
                    return LocalDateTime.parse(timestamp, SECOND_FORMATTER);
                } catch (DateTimeParseException ignored) {
                }
            }
            return LocalDateTime.parse(timestamp, MINUTE_FORMATTER);
        } catch (DateTimeParseException e) {
            if (allowSeconds) {
                throw new IllegalArgumentException("Invalid timestamp format. Use YYYY-MM-DDTHH:MM or YYYY-MM-DDTHH:MM:SS");
            }
            throw new IllegalArgumentException("Invalid date format. Use YYYY-MM-DDTHH:MM");
        }
    }
}
