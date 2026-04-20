package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Service.FullChainReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class    CostReportController {

    @Autowired
    private FullChainReportService fullChainReportService;

    @GetMapping("/api/cost/report/export")
    @ResponseBody
    public ResponseEntity<?> exportPdf(@RequestParam(name = "id", required = false) Long id) {
        if (id == null || id <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "id is required."));
        }

        try {
            byte[] pdf = fullChainReportService.exportFullChainReportPdf(id);
            String filename = "cost-report-" + id + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
