package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Repository.LoadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class LoadService {

    @Autowired
    private LoadRepository loadRepository;

    private String PYTHON_LOAD_API_URL = "http://localhost:5001/predict";

    public double predictAndSave(LoadRequest request) {
        double prediction = predictValue(request);
        request.setPredictedLoad(prediction);
        LoadRequest saved = loadRepository.save(request);
        return saved.getPredictedLoad();
    }

    public double predictValue(LoadRequest request) {
        double prediction;

        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> apiRequest = new HashMap<>();
            apiRequest.put("timestamp", request.getTimestamp() != null ? request.getTimestamp().toString() : "2026-04-03T12:00:00");
            apiRequest.put("temperature", request.getTemperature());
            apiRequest.put("humidity", request.getHumidity());
            apiRequest.put("public_event", request.getPublicEvent());

            Map<String, Double> response = restTemplate.postForObject(PYTHON_LOAD_API_URL, apiRequest, Map.class);

            if (response != null && response.containsKey("load_demand")) {
                prediction = ((Number) response.get("load_demand")).doubleValue();
                System.out.println("Success! Real AI Load Prediction: " + prediction + " kW");
            } else {
                throw new RuntimeException("Invalid response from Load AI");
            }

        } catch (Exception e) {
            System.err.println("Load Prediction Server Unreachable: " + e.getMessage());
            System.out.println("Using Load Fallback...");
            prediction = 1500.0 + (request.getTemperature() * 10) + (request.getHumidity() * 2);
        }

        return prediction;
    }

    public void updateRequestWithAnomalyInfo(LoadRequest request) {
        loadRepository.save(request);
    }

    public LoadRequest getRequestById(Long id) {
        Optional<LoadRequest> result = loadRepository.findById(id);
        return result.orElse(null);
    }

    public void updateFeedback(Long id, boolean agreed) {
        LoadRequest request = loadRepository.findById(id).orElse(null);
        if (request != null) {
            request.setFeedbackGiven(true);
            request.setFeedbackAgreed(agreed);
            loadRepository.save(request);
        }
    }
}
