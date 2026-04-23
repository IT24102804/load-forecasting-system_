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

import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;

import java.util.Map;

@Controller
public class FullChainReportController {

    @Autowired
    private FullChainReportService fullChainReportService;

    @GetMapping("/reports")
    public String reportsPage(HttpSession session, Model model) {
        Integer userId = (Integer) session.getAttribute("userid");
        if (userId == null) {
            return "redirect:/";
        }
        model.addAttribute("activeNav", "reports");
        return "reports/index";
    }

    @GetMapping("/api/reports/full-chain/export")
    @ResponseBody
    public ResponseEntity<?> exportPdf(@RequestParam(name = "costRunId", required = false) Long costRunId,
                                       @RequestParam(name = "preview", required = false, defaultValue = "false") boolean preview) {
        if (costRunId == null || costRunId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "costRunId is required."));
        }

        try {
            byte[] pdf = fullChainReportService.exportFullChainReportPdf(costRunId);
            String filename = "full-chain-report-" + costRunId + ".pdf";
            String disposition = (preview ? "inline" : "attachment") + "; filename=\"" + filename + "\"";
            ResponseEntity<byte[]> ok = ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(MediaType.parseMediaType("application/pdf"))
                    .body(pdf);
            return ok;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
