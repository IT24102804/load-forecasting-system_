package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.LoadRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GenerationMixService {

    @Value("${generationmix.service.url:http://localhost:5003/predict}")
    private String generationMixServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> predictGenerationMix(LoadRequest loadRequest, double reservoirPct) {
        if (loadRequest == null || loadRequest.getId() == null) {
            throw new IllegalArgumentException("A saved forecast is required before generating the mix.");
        }
        if (loadRequest.getTimestamp() == null) {
            throw new IllegalArgumentException("Forecast timestamp is missing for the selected prediction.");
        }
        if (reservoirPct < 0 || reservoirPct > 100) {
            throw new IllegalArgumentException("Reservoir percentage must be between 0 and 100.");
        }

        double estimatedLoadDemand = estimateDailyDemandMwh(loadRequest.getPredictedLoad());
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

    public double estimateDailyDemandMwh(double forecastLoad) {
        if (forecastLoad <= 0) {
            throw new IllegalArgumentException("Forecast load must be available before generating the mix.");
        }
        // The generation-mix model expects a daily energy-demand scale, so bridge the
        // saved forecast output into a 24-hour estimate rather than asking users to re-enter load.
        return round(forecastLoad * 24.0);
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
