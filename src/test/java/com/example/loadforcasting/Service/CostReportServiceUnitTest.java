package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.CostPredictionRun;
import com.example.loadforcasting.Entity.GenerationMixRun;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Repository.CostPredictionRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostReportServiceUnitTest {

    @Mock
    private CostPredictionRunRepository costPredictionRunRepository;

    @Mock
    private GenerationMixService generationMixService;

    @Mock
    private LoadService loadService;

    @InjectMocks
    private CostReportService costReportService;

    @Test
    void exportCostReportPdf_WithValidLinks_ReturnsPdfBytes() {
        CostPredictionRun costRun = new CostPredictionRun();
        costRun.setId(7L);
        costRun.setFoPrice(1000.0);
        costRun.setCoalPrice(2000.0);
        costRun.setDieselPrice(1500.0);
        costRun.setNaphthaPrice(1600.0);
        costRun.setUnitCost(52.0);

        GenerationMixRun mixRun = new GenerationMixRun();
        mixRun.setId(9L);
        mixRun.setForecastTimestamp(LocalDateTime.of(2026, 4, 13, 14, 0));
        mixRun.setEstimatedDailyDemandMwh(28940.21);
        mixRun.setMajorHydroMwh(8200.0);
        mixRun.setTotalCoalMwh(7100.0);
        mixRun.setTotalThermalMwh(4200.0);
        mixRun.setWindMwh(1450.0);
        mixRun.setSolarMwh(910.0);
        mixRun.setMiniHydroMwh(1800.0);

        LoadRequest loadRequest = new LoadRequest();
        loadRequest.setId(11L);
        loadRequest.setTimestamp(LocalDateTime.of(2026, 4, 13, 14, 0));
        loadRequest.setTemperature(28.0);
        loadRequest.setHumidity(80.0);
        loadRequest.setPublicEvent(1);
        loadRequest.setPredictedLoad(1205.842);

        mixRun.setLoadRequest(loadRequest);
        costRun.setGenerationMixRun(mixRun);

        when(costPredictionRunRepository.findById(7L)).thenReturn(Optional.of(costRun));
        when(generationMixService.getRunById(9L)).thenReturn(Optional.of(mixRun));

        byte[] pdf = costReportService.exportCostReportPdf(7L);

        assertNotNull(pdf);
        assertTrue(pdf.length > 100);
        assertTrue(new String(pdf, 0, Math.min(pdf.length, 8)).contains("%PDF"));
    }

    @Test
    void exportCostReportPdf_WhenCostRunMissing_Throws() {
        when(costPredictionRunRepository.findById(77L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> costReportService.exportCostReportPdf(77L));

        assertNotNull(ex.getMessage());
    }
}
