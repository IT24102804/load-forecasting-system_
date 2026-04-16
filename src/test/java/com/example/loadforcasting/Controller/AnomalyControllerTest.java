package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.Service.AnomalyDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnomalyControllerTest {

    @Mock
    private AnomalyDetectionService anomalyService;

    @Mock
    private Model model;

    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private AnomalyController anomalyController;

    private MockHttpSession loggedInSession;
    private MockHttpSession loggedOutSession;
    private Anomaly sampleAnomaly;



    @BeforeEach
        void setUp() {
            loggedInSession = new MockHttpSession();
            loggedInSession.setAttribute("userid", 1);
            loggedInSession.setAttribute("role", "Admin"); // ← add this line

            loggedOutSession = new MockHttpSession();

        sampleAnomaly = new Anomaly();
        sampleAnomaly.setId(1L);
        sampleAnomaly.setTimestamp(LocalDateTime.now());
        sampleAnomaly.setLoadDemand(9500.0);
        sampleAnomaly.setTemperature(38.0);
        sampleAnomaly.setHumidity(90.0);
        sampleAnomaly.setSeverity("HIGH");
        sampleAnomaly.setStatus("OPEN");
        sampleAnomaly.setReason("Critical deviation detected.");
        sampleAnomaly.setDetectedAt(LocalDateTime.now());
    }

    // ===== DASHBOARD TESTS =====

    @Test
    void dashboard_LoggedIn_ReturnsDashboardView() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAnomalies", 5L);
        stats.put("highCount", 2L);
        when(anomalyService.getDashboardStats()).thenReturn(stats);

        String view = anomalyController.dashboard(loggedInSession, model);

        assertEquals("anomaly/dashboard", view);
        verify(model, times(1)).addAllAttributes(stats);
    }

    @Test
    void dashboard_NotLoggedIn_RedirectsToLogin() {
        String view = anomalyController.dashboard(loggedOutSession, model);

        assertEquals("redirect:/", view);
        verify(anomalyService, never()).getDashboardStats();
    }

    // ===== LIST TESTS =====

    @Test
    void list_NoFilter_ReturnsAllAnomalies() {
        List<Anomaly> anomalies = Arrays.asList(sampleAnomaly);
        when(anomalyService.getAllAnomalies()).thenReturn(anomalies);

        String view = anomalyController.list(null, null, loggedInSession, model);

        assertEquals("anomaly/list", view);
        verify(model).addAttribute("anomalies", anomalies);
        verify(model).addAttribute("totalCount", 1);
    }

    @Test
    void list_FilterBySeverityHigh_ReturnsFilteredList() {
        List<Anomaly> highAnomalies = Arrays.asList(sampleAnomaly);
        when(anomalyService.getAnomaliesBySeverity("HIGH")).thenReturn(highAnomalies);

        String view = anomalyController.list("HIGH", null, loggedInSession, model);

        assertEquals("anomaly/list", view);
        verify(anomalyService).getAnomaliesBySeverity("HIGH");
        verify(model).addAttribute("filterSeverity", "HIGH");
    }

    @Test
    void list_FilterByStatusOpen_ReturnsFilteredList() {
        List<Anomaly> openAnomalies = Arrays.asList(sampleAnomaly);
        when(anomalyService.getAnomaliesByStatus("OPEN")).thenReturn(openAnomalies);

        String view = anomalyController.list(null, "OPEN", loggedInSession, model);

        assertEquals("anomaly/list", view);
        verify(anomalyService).getAnomaliesByStatus("OPEN");
        verify(model).addAttribute("filterStatus", "OPEN");
    }

    @Test
    void list_NotLoggedIn_RedirectsToLogin() {
        String view = anomalyController.list(null, null, loggedOutSession, model);

        assertEquals("redirect:/", view);
        verify(anomalyService, never()).getAllAnomalies();
    }

    @Test
    void list_EmptyDatabase_ReturnsEmptyList() {
        when(anomalyService.getAllAnomalies()).thenReturn(Arrays.asList());

        String view = anomalyController.list(null, null, loggedInSession, model);

        assertEquals("anomaly/list", view);
        verify(model).addAttribute("totalCount", 0);
    }

    // ===== DETAIL TESTS =====

    @Test
    void detail_ExistingAnomaly_ReturnsDetailView() {
        when(anomalyService.getAnomalyById(1L)).thenReturn(Optional.of(sampleAnomaly));

        String view = anomalyController.detail(1L, loggedInSession, model, redirectAttributes);

        assertEquals("anomaly/detail", view);
        verify(model).addAttribute("anomaly", sampleAnomaly);
    }

    @Test
    void detail_NonExistingAnomaly_RedirectsToList() {
        when(anomalyService.getAnomalyById(99L)).thenReturn(Optional.empty());

        String view = anomalyController.detail(99L, loggedInSession, model, redirectAttributes);

        assertEquals("redirect:/anomaly/list", view);
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), anyString());
    }

    @Test
    void detail_NotLoggedIn_RedirectsToLogin() {
        String view = anomalyController.detail(1L, loggedOutSession, model, redirectAttributes);

        assertEquals("redirect:/", view);
        verify(anomalyService, never()).getAnomalyById(any());
    }

    // ===== ACKNOWLEDGE TESTS =====

    @Test
    void acknowledge_ValidAnomaly_RedirectsToDetail() {
        when(anomalyService.acknowledgeAnomaly(1L)).thenReturn(sampleAnomaly);

        String view = anomalyController.acknowledge(1L, loggedInSession, redirectAttributes);

        assertEquals("redirect:/anomaly/1", view);
        verify(redirectAttributes).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void acknowledge_ServiceThrowsException_RedirectsWithError() {
        when(anomalyService.acknowledgeAnomaly(99L))
            .thenThrow(new RuntimeException("Anomaly not found: 99"));

        String view = anomalyController.acknowledge(99L, loggedInSession, redirectAttributes);

        assertEquals("redirect:/anomaly/99", view);
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), anyString());
    }

    @Test
    void acknowledge_NotLoggedIn_RedirectsToLogin() {
        String view = anomalyController.acknowledge(1L, loggedOutSession, redirectAttributes);

        assertEquals("redirect:/", view);
        verify(anomalyService, never()).acknowledgeAnomaly(any());
    }

    // ===== RESOLVE TESTS =====

    @Test
    void resolve_ValidAnomalyWithNote_RedirectsToDetail() {
        when(anomalyService.resolveAnomaly(1L, "Sensor recalibrated."))
            .thenReturn(sampleAnomaly);

        String view = anomalyController.resolve(
            1L, "Sensor recalibrated.", loggedInSession, redirectAttributes);

        assertEquals("redirect:/anomaly/1", view);
        verify(redirectAttributes).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void resolve_EmptyNote_UsesDefaultNote() {
        when(anomalyService.resolveAnomaly(eq(1L), anyString())).thenReturn(sampleAnomaly);

        String view = anomalyController.resolve(
            1L, "", loggedInSession, redirectAttributes);

        assertEquals("redirect:/anomaly/1", view);
        // Verify it was called with the default note instead of empty string
        verify(anomalyService).resolveAnomaly(eq(1L), eq("Resolved by operator."));
    }

    @Test
    void resolve_NullNote_UsesDefaultNote() {
        when(anomalyService.resolveAnomaly(eq(1L), anyString())).thenReturn(sampleAnomaly);

        String view = anomalyController.resolve(
            1L, null, loggedInSession, redirectAttributes);

        assertEquals("redirect:/anomaly/1", view);
        verify(anomalyService).resolveAnomaly(eq(1L), eq("Resolved by operator."));
    }

    @Test
    void resolve_ServiceThrowsException_RedirectsWithError() {
        when(anomalyService.resolveAnomaly(anyLong(), anyString()))
            .thenThrow(new RuntimeException("Not found"));

        String view = anomalyController.resolve(
            1L, "Some note", loggedInSession, redirectAttributes);

        assertEquals("redirect:/anomaly/1", view);
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), anyString());
    }

    @Test
    void resolve_NotLoggedIn_RedirectsToLogin() {
        String view = anomalyController.resolve(
            1L, "Some note", loggedOutSession, redirectAttributes);

        assertEquals("redirect:/", view);
        verify(anomalyService, never()).resolveAnomaly(any(), any());
    }
}
