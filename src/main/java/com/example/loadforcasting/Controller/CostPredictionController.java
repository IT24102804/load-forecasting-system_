package com.example.loadforcasting.Controller;



import com.example.loadforcasting.Entity.CostPredictionRun;

import com.example.loadforcasting.Entity.GenerationMixRun;

import com.example.loadforcasting.Repository.CostPredictionRunRepository;

import com.example.loadforcasting.Service.CostPredictionService;

import com.example.loadforcasting.Service.GenerationMixService;

import com.example.loadforcasting.dto.CostPredictionRequest;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;

import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.*;



import java.time.LocalDate;

import java.time.LocalDateTime;

import java.time.LocalTime;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;
import java.util.Comparator;



@Controller

public class CostPredictionController {



    @Autowired

    private GenerationMixService generationMixService;



    @Autowired

    private CostPredictionService costPredictionService;



    @Autowired

    private CostPredictionRunRepository costPredictionRunRepository;



    @GetMapping("/cost")

    public String costPage(HttpSession session, Model model) {

        Integer userId = (Integer) session.getAttribute("userid");

        if (userId == null) {

            return "redirect:/";

        }



        List<GenerationMixRun> runs = generationMixService.getAllRuns();

        model.addAttribute("runs", runs);

        List<CostPredictionRun> costRuns = costPredictionRunRepository.findAllByOrderByCreatedAtDesc();

        model.addAttribute("costRuns", costRuns);

        model.addAttribute("activeNav", "cost");

        return "cost/index";

    }

    @GetMapping("/cost/recent")
    public String recentCostEntriesPage(HttpSession session, Model model) {
        Integer userId = (Integer) session.getAttribute("userid");
        if (userId == null) {
            return "redirect:/";
        }

        List<CostPredictionRun> costRuns = costPredictionRunRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("costRuns", costRuns);
        model.addAttribute("activeNav", "cost");
        return "cost/recent";
    }



    @PostMapping("/api/cost/predict")

    @ResponseBody

    public ResponseEntity<?> predict(@RequestBody CostPredictionRequest request) {

        String validationError = validateRequest(request);

        if (validationError != null) {

            return ResponseEntity.badRequest().body(Map.of("error", validationError));

        }



        GenerationMixRun run = generationMixService.getRunById(request.getRunId()).orElse(null);

        if (run == null) {

            return ResponseEntity.status(404).body(Map.of("error", "Saved generation mix run not found."));

        }

        LocalDateTime forecastTs = run.getForecastTimestamp();

        if (forecastTs == null) {

            return ResponseEntity.badRequest().body(Map.of("error", "Saved generation mix run is missing forecast timestamp."));

        }



        try {

            boolean forceNew = Boolean.TRUE.equals(request.getForceNew());

            return ResponseEntity.ok(forceNew

                    ? costPredictionService.predictCost(

                            run,

                            request.getFoPrice(),

                            request.getCoalPrice(),

                            request.getDieselPrice(),

                            request.getNaphthaPrice(),

                            true

                    )

                    : costPredictionService.predictCost(

                            run,

                            request.getFoPrice(),

                            request.getCoalPrice(),

                            request.getDieselPrice(),

                            request.getNaphthaPrice()

                    ));

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));

        } catch (RuntimeException e) {

            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));

        }

    }



    @GetMapping("/api/cost/recent/points")

    @ResponseBody

    public ResponseEntity<?> recentPoints() {

        var runs = costPredictionRunRepository.findTop200ByOrderByForecastTimestampAsc();

        List<Map<String, Object>> points = runs.stream()

                .filter(r -> r != null && r.getForecastTimestamp() != null && r.getUnitCost() != null)

                .map(r -> {

                    Map<String, Object> out = new LinkedHashMap<>();

                    out.put("timestamp", r.getForecastTimestamp().toString());

                    out.put("unitCost", r.getUnitCost());

                    out.put("costRunId", r.getId());

                    out.put("setupId", r.getGenerationMixResultId());

                    return out;

                })

                .toList();

        return ResponseEntity.ok(points);

    }


    @GetMapping("/api/cost/by-forecast-date")
    @ResponseBody
    public ResponseEntity<?> latestCostByForecastDate(@RequestParam String date) {
        try {
            LocalDate parsed = LocalDate.parse(date);
            LocalDateTime fromTs = parsed.atStartOfDay();
            LocalDateTime toTs = parsed.atTime(LocalTime.MAX);

            CostPredictionRun run = costPredictionRunRepository
                    .findFirstByForecastTimestampBetweenOrderByCreatedAtDesc(fromTs, toTs)
                    .orElse(null);

            if (run == null) {
                return ResponseEntity.ok(Map.of("found", false));
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("found", true);
            out.put("id", run.getId());
            out.put("runId", run.getGenerationMixResultId());
            out.put("forecastTimestamp", run.getForecastTimestamp() != null ? run.getForecastTimestamp().toString() : null);
            out.put("foPrice", run.getFoPrice());
            out.put("coalPrice", run.getCoalPrice());
            out.put("dieselPrice", run.getDieselPrice());
            out.put("naphthaPrice", run.getNaphthaPrice());
            out.put("unitCost", run.getUnitCost());
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format. Use YYYY-MM-DD"));
        }
    }

    @GetMapping("/api/cost/summary")
    @ResponseBody
    public ResponseEntity<?> costSummary(@RequestParam String forecastDate) {
        try {
            LocalDate parsed = LocalDate.parse(forecastDate);
            LocalDate fromDate = parsed.minusDays(6);

            LocalDateTime fromTs = fromDate.atStartOfDay();
            LocalDateTime toTs = parsed.atTime(LocalTime.MAX);

            List<CostPredictionRun> runs = costPredictionRunRepository
                    .findAllByForecastTimestampBetweenOrderByForecastTimestampAsc(fromTs, toTs);

            List<CostPredictionRun> valid = runs.stream()
                    .filter(r -> r != null && r.getUnitCost() != null)
                    .toList();

            if (valid.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "found", false,
                        "forecastDate", parsed.toString(),
                        "from", fromDate.toString(),
                        "to", parsed.toString(),
                        "points", List.of()
                ));
            }

            double peak = valid.stream()
                    .map(CostPredictionRun::getUnitCost)
                    .filter(v -> v != null && Double.isFinite(v))
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElse(Double.NaN);

            double avg = valid.stream()
                    .map(CostPredictionRun::getUnitCost)
                    .filter(v -> v != null && Double.isFinite(v))
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(Double.NaN);

            CostPredictionRun latest = valid.stream()
                    .filter(r -> r.getCreatedAt() != null)
                    .max(Comparator.comparing(CostPredictionRun::getCreatedAt))
                    .orElse(valid.get(valid.size() - 1));

            long uniqueDates = valid.stream()
                    .filter(r -> r.getForecastTimestamp() != null)
                    .map(r -> r.getForecastTimestamp().toLocalDate())
                    .distinct()
                    .count();

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("found", true);
            out.put("forecastDate", parsed.toString());
            out.put("from", fromDate.toString());
            out.put("to", parsed.toString());
            out.put("peakCost", Double.isFinite(peak) ? peak : null);
            out.put("averageCost", Double.isFinite(avg) ? avg : null);
            out.put("latestCost", latest.getUnitCost());
            out.put("predictions", uniqueDates);
            out.put("latestCostRunId", latest.getId());
            List<Map<String, Object>> points = valid.stream()
                    .filter(r -> r.getForecastTimestamp() != null)
                    .map(r -> {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("forecastTimestamp", r.getForecastTimestamp().toString());
                        p.put("unitCost", r.getUnitCost());
                        p.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
                        p.put("costRunId", r.getId());
                        return p;
                    })
                    .toList();
            out.put("points", points);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid forecastDate format. Use YYYY-MM-DD"));
        }
    }



    @PutMapping("/api/cost/{id}")

    @ResponseBody

    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CostPredictionRequest request) {

        String validationError = validateRequest(request);

        if (validationError != null) {

            return ResponseEntity.badRequest().body(Map.of("error", validationError));

        }



        CostPredictionRun existingRun = costPredictionRunRepository.findById(id).orElse(null);

        if (existingRun == null) {

            return ResponseEntity.status(404).body(Map.of("error", "Saved cost prediction run not found."));

        }



        GenerationMixRun run = generationMixService.getRunById(request.getRunId()).orElse(null);

        if (run == null) {

            return ResponseEntity.status(404).body(Map.of("error", "Saved generation mix run not found."));

        }

        LocalDateTime forecastTs = run.getForecastTimestamp();

        if (forecastTs == null) {

            return ResponseEntity.badRequest().body(Map.of("error", "Saved generation mix run is missing forecast timestamp."));

        }



        try {

            return ResponseEntity.ok(costPredictionService.updateCostRun(

                    existingRun,

                    run,

                    request.getFoPrice(),

                    request.getCoalPrice(),

                    request.getDieselPrice(),

                    request.getNaphthaPrice()

            ));

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));

        } catch (RuntimeException e) {

            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));

        }

    }



    @DeleteMapping("/api/cost/{id}")

    @ResponseBody

    public ResponseEntity<?> delete(@PathVariable Long id) {

        try {

            costPredictionService.deleteCostRun(id);

            return ResponseEntity.ok(Map.of("status", "deleted", "id", id));

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));

        }

    }



    @GetMapping("/api/cost/history")

    @ResponseBody

    public ResponseEntity<?> history(@RequestParam String from, @RequestParam String to) {

        try {

            LocalDate fromDate = LocalDate.parse(from);

            LocalDate toDate = LocalDate.parse(to);

            if (toDate.isBefore(fromDate)) {

                return ResponseEntity.badRequest().body(Map.of("error", "To date must be on or after From date."));

            }

            LocalDateTime fromTs = fromDate.atStartOfDay();

            LocalDateTime toTs = toDate.atTime(LocalTime.MAX);

            List<CostPredictionRun> runs = costPredictionRunRepository

                    .findAllByForecastTimestampBetweenOrderByForecastTimestampAsc(fromTs, toTs);

            return ResponseEntity.ok(runs);

        } catch (Exception e) {

            return ResponseEntity.badRequest().body(Map.of("error", "Invalid from/to date format. Use YYYY-MM-DD"));

        }

    }



    private String validateRequest(CostPredictionRequest request) {

        if (request == null) {

            return "Request body is required.";

        }

        if (request.getRunId() == null || request.getRunId() <= 0) {

            return "A valid generation mix run is required.";

        }

        if (request.getFoPrice() == null || request.getCoalPrice() == null || request.getDieselPrice() == null || request.getNaphthaPrice() == null) {

            return "All fuel prices are required.";

        }

        if (!isValidPrice(request.getFoPrice()) || !isValidPrice(request.getCoalPrice())

                || !isValidPrice(request.getDieselPrice()) || !isValidPrice(request.getNaphthaPrice())) {

            return "Fuel prices must be valid numbers greater than 0 and not more than 2000.";

        }

        return null;

    }



    private boolean isValidPrice(Double value) {

        return value != null && Double.isFinite(value) && value > 0 && value <= 2000;

    }

}

