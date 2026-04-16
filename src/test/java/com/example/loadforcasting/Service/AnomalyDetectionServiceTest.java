package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.Repository.AnomalyRepository;
import com.example.loadforcasting.Repository.LoadDataRepository;
import com.example.loadforcasting.Entity.LoadData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnomalyDetectionServiceTest {

    @Mock
    private AnomalyRepository anomalyRepository;

    @Mock
    private LoadDataRepository loadDataRepository;

    @InjectMocks
    private AnomalyDetectionService anomalyService;

    private Anomaly sampleAnomaly;
    private List<LoadData> sampleLoadData;

    @BeforeEach
    void setUp() {
        sampleAnomaly = new Anomaly();
        sampleAnomaly.setId(1L);
        sampleAnomaly.setTimestamp(LocalDateTime.now());
        sampleAnomaly.setLoadDemand(9500.0);
        sampleAnomaly.setTemperature(38.0);
        sampleAnomaly.setHumidity(90.0);
        sampleAnomaly.setHourOfDay(14);
        sampleAnomaly.setDayOfWeek(3);
        sampleAnomaly.setMonth(6);
        sampleAnomaly.setAnomalyScore(0.85);
        sampleAnomaly.setSeverity("HIGH");
        sampleAnomaly.setReason("Load demand significantly deviates from hourly mean.");
        sampleAnomaly.setStatus("OPEN");
        sampleAnomaly.setDetectedAt(LocalDateTime.now());

        // Build realistic historical data for hour 14
        sampleLoadData = buildSampleLoadData();
    }

    // ===== DASHBOARD STATS TESTS =====

    @Test
    void getDashboardStats_ReturnsAllStatKeys() {
        when(anomalyRepository.count()).thenReturn(10L);
        when(anomalyRepository.countBySeverity("HIGH")).thenReturn(3L);
        when(anomalyRepository.countBySeverity("MEDIUM")).thenReturn(4L);
        when(anomalyRepository.countBySeverity("LOW")).thenReturn(3L);
        when(anomalyRepository.countByStatus("OPEN")).thenReturn(5L);
        when(anomalyRepository.countByStatus("RESOLVED")).thenReturn(5L);
        when(anomalyRepository.countTodaysAnomalies()).thenReturn(2L);
        when(anomalyRepository.findTop10ByOrderByDetectedAtDesc())
            .thenReturn(Arrays.asList(sampleAnomaly));

        Map<String, Object> stats = anomalyService.getDashboardStats();

        assertNotNull(stats);
        assertEquals(10L, stats.get("totalAnomalies"));
        assertEquals(3L, stats.get("highCount"));
        assertEquals(4L, stats.get("mediumCount"));
        assertEquals(3L, stats.get("lowCount"));
        assertEquals(5L, stats.get("openCount"));
        assertEquals(5L, stats.get("resolvedCount"));
        assertEquals(2L, stats.get("todayCount"));
        assertNotNull(stats.get("recentAnomalies"));
    }

    @Test
    void getDashboardStats_NoAnomalies_ReturnsZeroCounts() {
        when(anomalyRepository.count()).thenReturn(0L);
        when(anomalyRepository.countBySeverity(any())).thenReturn(0L);
        when(anomalyRepository.countByStatus(any())).thenReturn(0L);
        when(anomalyRepository.countTodaysAnomalies()).thenReturn(0L);
        when(anomalyRepository.findTop10ByOrderByDetectedAtDesc())
            .thenReturn(Arrays.asList());

        Map<String, Object> stats = anomalyService.getDashboardStats();

        assertEquals(0L, stats.get("totalAnomalies"));
        assertEquals(0L, stats.get("highCount"));
    }

    // ===== GET ANOMALY TESTS =====

    @Test
    void getAllAnomalies_ReturnsListOrderedByDate() {
        List<Anomaly> anomalies = Arrays.asList(sampleAnomaly);
        when(anomalyRepository.findAllByOrderByDetectedAtDesc()).thenReturn(anomalies);

        List<Anomaly> result = anomalyService.getAllAnomalies();

        assertEquals(1, result.size());
        verify(anomalyRepository, times(1)).findAllByOrderByDetectedAtDesc();
    }

    @Test
    void getAnomalyById_ExistingId_ReturnsAnomaly() {
        when(anomalyRepository.findById(1L)).thenReturn(Optional.of(sampleAnomaly));

        Optional<Anomaly> result = anomalyService.getAnomalyById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("HIGH", result.get().getSeverity());
    }

    @Test
    void getAnomalyById_NonExistingId_ReturnsEmpty() {
        when(anomalyRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Anomaly> result = anomalyService.getAnomalyById(99L);

        assertFalse(result.isPresent());
    }

    @Test
    void getAnomaliesBySeverity_High_ReturnsHighSeverityList() {
        List<Anomaly> highAnomalies = Arrays.asList(sampleAnomaly);
        when(anomalyRepository.findBySeverityOrderByDetectedAtDesc("HIGH"))
            .thenReturn(highAnomalies);

        List<Anomaly> result = anomalyService.getAnomaliesBySeverity("HIGH");

        assertEquals(1, result.size());
        assertEquals("HIGH", result.get(0).getSeverity());
    }

    @Test
    void getAnomaliesByStatus_Open_ReturnsOpenList() {
        List<Anomaly> openAnomalies = Arrays.asList(sampleAnomaly);
        when(anomalyRepository.findByStatusOrderByDetectedAtDesc("OPEN"))
            .thenReturn(openAnomalies);

        List<Anomaly> result = anomalyService.getAnomaliesByStatus("OPEN");

        assertEquals(1, result.size());
        assertEquals("OPEN", result.get(0).getStatus());
    }

    // ===== ACKNOWLEDGE TESTS =====

    @Test
    void acknowledgeAnomaly_OpenAnomaly_ChangesStatusToAcknowledged() {
        when(anomalyRepository.findById(1L)).thenReturn(Optional.of(sampleAnomaly));
        when(anomalyRepository.save(any(Anomaly.class))).thenReturn(sampleAnomaly);

        Anomaly result = anomalyService.acknowledgeAnomaly(1L);

        assertEquals("ACKNOWLEDGED", result.getStatus());
        verify(anomalyRepository, times(1)).save(sampleAnomaly);
    }

    @Test
    void acknowledgeAnomaly_NonExistingId_ThrowsException() {
        when(anomalyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> anomalyService.acknowledgeAnomaly(99L));

        verify(anomalyRepository, never()).save(any());
    }

    // ===== RESOLVE TESTS =====

    @Test
    void resolveAnomaly_ValidAnomaly_ChangesStatusToResolved() {
        when(anomalyRepository.findById(1L)).thenReturn(Optional.of(sampleAnomaly));
        when(anomalyRepository.save(any(Anomaly.class))).thenReturn(sampleAnomaly);

        Anomaly result = anomalyService.resolveAnomaly(1L, "Investigated and resolved.");

        assertEquals("RESOLVED", result.getStatus());
        assertEquals("Investigated and resolved.", result.getResolutionNote());
        assertNotNull(result.getResolvedAt());
        verify(anomalyRepository, times(1)).save(sampleAnomaly);
    }

    @Test
    void resolveAnomaly_NonExistingId_ThrowsException() {
        when(anomalyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> anomalyService.resolveAnomaly(99L, "some note"));
    }

    @Test
    void resolveAnomaly_SetsResolvedAtTimestamp() {
        when(anomalyRepository.findById(1L)).thenReturn(Optional.of(sampleAnomaly));
        when(anomalyRepository.save(any(Anomaly.class))).thenReturn(sampleAnomaly);

        anomalyService.resolveAnomaly(1L, "Fixed");

        assertNotNull(sampleAnomaly.getResolvedAt());
    }

    // ===== ANOMALY ENTITY HELPER METHOD TESTS =====

    @Test
    void anomaly_GetSeverityBadgeClass_HighReturnsDanger() {
        sampleAnomaly.setSeverity("HIGH");
        assertEquals("badge-high", sampleAnomaly.getSeverityBadgeClass());
    }

    @Test
    void anomaly_GetSeverityBadgeClass_MediumReturnsWarning() {
        sampleAnomaly.setSeverity("MEDIUM");
        assertEquals("badge-medium", sampleAnomaly.getSeverityBadgeClass());
    }

    @Test
    void anomaly_GetSeverityBadgeClass_LowReturnsSuccess() {
        sampleAnomaly.setSeverity("LOW");
        assertEquals("badge-low", sampleAnomaly.getSeverityBadgeClass());
    }

    @Test
    void anomaly_GetStatusBadgeClass_OpenReturnsDanger() {
        sampleAnomaly.setStatus("OPEN");
        assertEquals("bg-danger", sampleAnomaly.getStatusBadgeClass());
    }

    @Test
    void anomaly_GetStatusBadgeClass_ResolvedReturnsSuccess() {
        sampleAnomaly.setStatus("RESOLVED");
        assertEquals("bg-success", sampleAnomaly.getStatusBadgeClass());
    }

    @Test
    void anomaly_GetFormattedDate_ReturnsNonNullString() {
        sampleAnomaly.setDetectedAt(LocalDateTime.of(2026, 3, 28, 14, 30));
        String formatted = sampleAnomaly.getFormattedDate();

        assertNotNull(formatted);
        assertTrue(formatted.contains("2026"));
        assertTrue(formatted.contains("Mar"));
    }

    @Test
    void anomaly_GetFormattedDate_NullDetectedAt_ReturnsEmptyString() {
        sampleAnomaly.setDetectedAt(null);
        assertEquals("", sampleAnomaly.getFormattedDate());
    }

    // ===== SECURITY-RELATED TESTS =====

    @Test
    void resolveAnomaly_EmptyNote_StillResolves() {
        when(anomalyRepository.findById(1L)).thenReturn(Optional.of(sampleAnomaly));
        when(anomalyRepository.save(any(Anomaly.class))).thenReturn(sampleAnomaly);

        // Empty note should not throw — service handles it
        assertDoesNotThrow(() -> anomalyService.resolveAnomaly(1L, ""));
    }

    @Test
    void getAnomalyById_NegativeId_ReturnsEmpty() {
        when(anomalyRepository.findById(-1L)).thenReturn(Optional.empty());

        Optional<Anomaly> result = anomalyService.getAnomalyById(-1L);

        assertFalse(result.isPresent());
    }

    @Test
    void getAllAnomalies_EmptyDatabase_ReturnsEmptyList() {
        when(anomalyRepository.findAllByOrderByDetectedAtDesc())
            .thenReturn(Arrays.asList());

        List<Anomaly> result = anomalyService.getAllAnomalies();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ===== HELPER =====

    private List<LoadData> buildSampleLoadData() {
        LoadData d1 = new LoadData();
        d1.setHourOfDay(14);
        d1.setLoadDemand(3200.0);

        LoadData d2 = new LoadData();
        d2.setHourOfDay(14);
        d2.setLoadDemand(3400.0);

        LoadData d3 = new LoadData();
        d3.setHourOfDay(14);
        d3.setLoadDemand(3300.0);

        LoadData d4 = new LoadData();
        d4.setHourOfDay(10); // different hour — should be excluded
        d4.setLoadDemand(2800.0);

        return Arrays.asList(d1, d2, d3, d4);
    }
}
