package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Repository.LoadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LoadService {

    @Autowired
    private LoadRepository loadRepository;

    /**
     * Predict load and save the request (your existing logic)
     */
    public double predictAndSave(LoadRequest request) {
        // ========== YOUR EXISTING PREDICTION LOGIC GOES HERE ==========
        // For now, using a placeholder prediction
        // Replace this with your actual model call
        double prediction = 1500.0 + (request.getTemperature() * 10) + (request.getHumidity() * 2);

        // Save the request with prediction
        request.setPredictedLoad(prediction);
        LoadRequest saved = loadRepository.save(request);

        return saved.getPredictedLoad();
    }

    /**
     * Save/update a load request (used for updating anomaly info)
     */
    public void updateRequestWithAnomalyInfo(LoadRequest request) {
        loadRepository.save(request);
    }

    /**
     * Get a load request by ID
     */
    public LoadRequest getRequestById(Long id) {
        Optional<LoadRequest> result = loadRepository.findById(id);
        return result.orElse(null);
    }

    /**
     * Update feedback for a specific prediction
     */
    public void updateFeedback(Long id, boolean agreed) {
        LoadRequest request = loadRepository.findById(id).orElse(null);
        if (request != null) {
            request.setFeedbackGiven(true);
            request.setFeedbackAgreed(agreed);
            loadRepository.save(request);
        }
    }
}