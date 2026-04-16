package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Service.GenerationMixService;
import com.example.loadforcasting.Service.LoadService;
import com.example.loadforcasting.dto.GenerationMixRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class GenerationMixController {

    @Autowired
    private LoadService loadService;

    @Autowired
    private GenerationMixService generationMixService;

    @GetMapping("/generation-mix")
    public String generationMixPage(@RequestParam(required = false) Long predictionId,
                                    HttpSession session,
                                    Model model) {
        Integer userId = (Integer) session.getAttribute("userid");
        if (userId == null) {
            return "redirect:/";
        }

        model.addAttribute("selectedPrediction", null);
        model.addAttribute("estimatedDailyDemand", null);
        model.addAttribute("pageInstruction",
                "Generate a forecast first, then open Generation Mix from that prediction result.");

        if (predictionId != null) {
            LoadRequest request = loadService.getRequestById(predictionId);
            if (request != null && request.getTimestamp() != null && request.getPredictedLoad() > 0) {
                model.addAttribute("selectedPrediction", request);
                model.addAttribute("estimatedDailyDemand",
                        generationMixService.estimateDailyDemandMwh(request.getPredictedLoad()));
            } else {
                model.addAttribute("pageError",
                        "Prediction #" + predictionId + " is not available. Generate a forecast first.");
            }
        }

        return "generationmix/index";
    }

    @PostMapping("/api/generation-mix/predict")
    @ResponseBody
    public ResponseEntity<?> predictGenerationMix(@RequestBody GenerationMixRequest request) {
        if (request.getPredictionId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "predictionId is required."));
        }
        if (request.getReservoirPct() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "reservoirPct is required."));
        }
        if (request.getReservoirPct() < 0 || request.getReservoirPct() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reservoir percentage must be between 0 and 100."));
        }

        LoadRequest loadRequest = loadService.getRequestById(request.getPredictionId());
        if (loadRequest == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Prediction not found for id " + request.getPredictionId() + "."));
        }

        try {
            return ResponseEntity.ok(
                    generationMixService.predictGenerationMix(loadRequest, request.getReservoirPct())
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }
}
