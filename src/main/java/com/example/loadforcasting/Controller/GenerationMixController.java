package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.GenerationMixRun;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Service.GenerationMixService;
import com.example.loadforcasting.Service.LoadService;
import com.example.loadforcasting.dto.GenerationMixRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

@Controller
public class GenerationMixController {

    @Autowired
    private LoadService loadService;

    @Autowired
    private GenerationMixService generationMixService;

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @GetMapping("/generation-mix")
    public String generationMixPage(@RequestParam(required = false) String predictionId,
                                    @RequestParam(required = false) String runId,
                                    @RequestParam(required = false) String reservoirPct,
                                    HttpSession session,
                                    Model model) {
        Integer userId = (Integer) session.getAttribute("userid");
        if (userId == null) {
            return "redirect:/";
        }

        List<GenerationMixRun> runs = generationMixService.getAllRuns();
        model.addAttribute("runs", runs);

        model.addAttribute("selectedPrediction", null);
        model.addAttribute("estimatedDailyDemand", null);
        Long parsedRunId = null;
        Double parsedReservoirPct = null;
        Long parsedPredictionId = null;

        if (runId != null && !runId.isBlank()) {
            try {
                parsedRunId = Long.parseLong(runId);
            } catch (NumberFormatException ignored) {
                model.addAttribute("pageError", "Invalid saved mix id.");
            }
        }
        if (reservoirPct != null && !reservoirPct.isBlank()) {
            try {
                parsedReservoirPct = Double.parseDouble(reservoirPct);
            } catch (NumberFormatException ignored) {
                model.addAttribute("pageError", "Invalid reservoir percentage.");
            }
        }
        if (predictionId != null && !predictionId.isBlank()) {
            try {
                parsedPredictionId = Long.parseLong(predictionId);
            } catch (NumberFormatException ignored) {
                model.addAttribute("pageError", "Invalid prediction id.");
            }
        }

        model.addAttribute("selectedRunId", parsedRunId);
        model.addAttribute("prefillReservoirPct", parsedReservoirPct);
        model.addAttribute("pageInstruction",
                "Generate a forecast first, then open Generation Mix from that prediction result.");

        if (parsedPredictionId != null) {
            LoadRequest request = loadService.getRequestById(parsedPredictionId);
            if (request != null && request.getTimestamp() != null && request.getPredictedLoad() > 0) {
                model.addAttribute("selectedPrediction", request);
                model.addAttribute("estimatedDailyDemand",
                        generationMixService.getEstimatedDailyDemandForView(request));
            } else {
                model.addAttribute("pageError",
                        "Prediction #" + parsedPredictionId + " is not available. Generate a forecast first.");
            }
        }

        return "generationmix/index";
    }

    @GetMapping("/api/generation-mix/runs")
    @ResponseBody
    public List<GenerationMixRun> getRuns() {
        return generationMixService.getAllRuns();
    }

    @GetMapping("/api/generation-mix/runs/{id}")
    @ResponseBody
    public ResponseEntity<?> getRun(@PathVariable Long id) {
        return generationMixService.getRunById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Saved mix not found")));
    }

    @DeleteMapping("/api/generation-mix/runs/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteRun(@PathVariable Long id) {
        try {
            generationMixService.deleteRun(id);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/generation-mix/runs/{id}/rerun")
    @ResponseBody
    public ResponseEntity<?> rerunSavedMix(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Object predictionIdObj = body.get("predictionId");
            Object reservoirPctObj = body.get("reservoirPct");
            if (!(predictionIdObj instanceof Number) || !(reservoirPctObj instanceof Number)) {
                return ResponseEntity.badRequest().body(Map.of("error", "predictionId and reservoirPct are required."));
            }

            Long predictionId = ((Number) predictionIdObj).longValue();
            double reservoirPct = ((Number) reservoirPctObj).doubleValue();

            LoadRequest loadRequest = loadService.getRequestById(predictionId);
            if (loadRequest == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Prediction not found for id " + predictionId + "."));
            }
            if (loadRequest.getTimestamp() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Forecast timestamp is missing for the selected prediction."));
            }

            GenerationMixRun updated = generationMixService.rerunAndUpdate(id, loadRequest, reservoirPct);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
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
        if (loadRequest.getTimestamp() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Forecast timestamp is missing for the selected prediction."));
        }

        try {
            boolean forceNew = Boolean.TRUE.equals(request.getForceNew());
            return ResponseEntity.ok(
                    forceNew
                            ? generationMixService.predictGenerationMix(loadRequest, request.getReservoirPct(), true)
                            : generationMixService.predictGenerationMix(loadRequest, request.getReservoirPct())
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/generation-mix/db-info")
    @ResponseBody
    public ResponseEntity<?> generationMixDbInfo() {
        try {
            Long runCount = (long) generationMixService.getAllRuns().size();

            String configuredUrl = environment.getProperty("spring.datasource.url");
            String configuredUser = environment.getProperty("spring.datasource.username");

            String catalog = null;
            String jdbcUrl = null;
            if (dataSource != null) {
                try (Connection connection = dataSource.getConnection()) {
                    catalog = connection.getCatalog();
                    if (connection.getMetaData() != null) {
                        jdbcUrl = connection.getMetaData().getURL();
                    }
                }
            }

            return ResponseEntity.ok(Map.of(
                    "configuredDatasourceUrl", configuredUrl,
                    "configuredDatasourceUser", configuredUser,
                    "connectedCatalog", catalog,
                    "connectedJdbcUrl", jdbcUrl,
                    "generationMixRunsCount", runCount
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
