package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.Service.AnomalyDetectionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/anomaly")
public class AnomalyController {

    @Autowired
    private AnomalyDetectionService anomalyService;

    // ===== Security check helper =====
    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("userid") != null;
    }


    private boolean isAdmin(HttpSession session) {
        String role = (String) session.getAttribute("role");
        return "Admin".equalsIgnoreCase(role) ||
                "Administrator".equalsIgnoreCase(role);
    }

    // =============================================
    // DASHBOARD — /anomaly
    // Shows summary cards + recent anomalies table
    // =============================================
    @GetMapping({"", "/"})
    public String dashboard(HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/";

        Map<String, Object> stats = anomalyService.getDashboardStats();
        model.addAllAttributes(stats);

        return "anomaly/dashboard";
    }

    // =============================================
    // LIST — /anomaly/list
    // Full table with filter controls
    // =============================================
    @GetMapping("/list")
    public String list(@RequestParam(required = false) String severity,
                       @RequestParam(required = false) String status,
                       HttpSession session,
                       Model model) {
        if (!isAdmin(session)) return "redirect:/";

        List<Anomaly> anomalies;

        if (severity != null && !severity.isEmpty()) {
            anomalies = anomalyService.getAnomaliesBySeverity(severity.toUpperCase());
            model.addAttribute("filterSeverity", severity);
        } else if (status != null && !status.isEmpty()) {
            anomalies = anomalyService.getAnomaliesByStatus(status.toUpperCase());
            model.addAttribute("filterStatus", status);
        } else {
            anomalies = anomalyService.getAllAnomalies();
        }

        model.addAttribute("anomalies", anomalies);
        model.addAttribute("totalCount", anomalies.size());

        return "anomaly/list";
    }

    // =============================================
    // DETAIL — /anomaly/{id}
    // Single anomaly detail view
    // =============================================
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model,
                         RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) return "redirect:/";

        return anomalyService.getAnomalyById(id)
                .map(anomaly -> {
                    model.addAttribute("anomaly", anomaly);
                    return "anomaly/detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Anomaly record #" + id + " was not found.");
                    return "redirect:/anomaly/list";
                });
    }

    // =============================================
    // ACKNOWLEDGE — /anomaly/{id}/acknowledge
    // =============================================
    @PostMapping("/{id}/acknowledge")
    public String acknowledge(@PathVariable Long id, HttpSession session,
                              RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) return "redirect:/";

        try {
            anomalyService.acknowledgeAnomaly(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Anomaly #" + id + " marked as Acknowledged.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
        }
        return "redirect:/anomaly/" + id;
    }

    // =============================================
    // RESOLVE — /anomaly/{id}/resolve
    // =============================================
    @PostMapping("/{id}/resolve")
    public String resolve(@PathVariable Long id,
                          @RequestParam(required = false) String resolutionNote,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) return "redirect:/";

        try {
            String note = (resolutionNote != null && !resolutionNote.isBlank())
                    ? resolutionNote : "Resolved by operator.";
            anomalyService.resolveAnomaly(id, note);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Anomaly #" + id + " has been resolved.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
        }
        return "redirect:/anomaly/" + id;
    }
}
