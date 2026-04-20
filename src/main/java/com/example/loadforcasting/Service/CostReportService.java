package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.CostPredictionRun;
import com.example.loadforcasting.Entity.GenerationMixRun;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Repository.CostPredictionRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class CostReportService {

    @Autowired
    private CostPredictionRunRepository costPredictionRunRepository;

    @Autowired
    private GenerationMixService generationMixService;

    @Autowired
    private LoadService loadService;

    public byte[] exportCostReportPdf(Long costRunId) {
        if (costRunId == null || costRunId <= 0) {
            throw new IllegalArgumentException("id is required.");
        }

        CostPredictionRun costRun = costPredictionRunRepository.findById(costRunId).orElse(null);
        if (costRun == null) {
            throw new IllegalArgumentException("Saved cost prediction run not found.");
        }

        GenerationMixRun mixRun = generationMixService.getRunById(costRun.getGenerationMixResultId()).orElse(null);
        if (mixRun == null) {
            throw new IllegalArgumentException("Linked generation mix run not found.");
        }

        LoadRequest loadRequest = mixRun.getLoadRequest();
        if (loadRequest == null) {
            loadRequest = loadService.getRequestById(mixRun.getLoadRequestId());
        }
        if (loadRequest == null) {
            throw new IllegalArgumentException("Linked load forecast not found.");
        }

        return buildPdf(costRun, mixRun, loadRequest);
    }

    private byte[] buildPdf(CostPredictionRun costRun, GenerationMixRun mixRun, LoadRequest loadRequest) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            String forecastTs = loadRequest.getTimestamp() != null ? dtf.format(loadRequest.getTimestamp()) : "--";

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            Paragraph title = new Paragraph("ELECTRICITY FORECAST REPORT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            Paragraph subtitle = new Paragraph("Sri Lanka Load Forecasting System", bodyFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitle);
            document.add(new Paragraph(" "));

            PdfPTable loadTable = new PdfPTable(2);
            loadTable.setWidthPercentage(100);
            addSectionHeader(loadTable, "LOAD FORECAST", sectionFont);
            addRow(loadTable, "Forecast Date/Time", forecastTs, bodyFont);
            addRow(loadTable, "Temperature (°C)", fmt(loadRequest.getTemperature()), bodyFont);
            addRow(loadTable, "Humidity (%)", fmt(loadRequest.getHumidity()), bodyFont);
            addRow(loadTable, "Public Event", String.valueOf(loadRequest.getPublicEvent()), bodyFont);
            addRow(loadTable, "Predicted Load (kW)", fmt(loadRequest.getPredictedLoad()), bodyFont);
            document.add(loadTable);
            document.add(new Paragraph(" "));

            PdfPTable forecastTable = new PdfPTable(2);
            forecastTable.setWidthPercentage(100);
            addSectionHeader(forecastTable, "FORECAST DETAILS", sectionFont);
            addRow(forecastTable, "Estimated Daily Demand (MWh)", fmt(mixRun.getEstimatedDailyDemandMwh()), bodyFont);
            document.add(forecastTable);
            document.add(new Paragraph(" "));

            PdfPTable mixTable = new PdfPTable(3);
            mixTable.setWidthPercentage(100);
            addSectionHeader(mixTable, "GENERATION MIX", sectionFont);
            double majorHydro = safe(mixRun.getMajorHydroMwh());
            double coal = safe(mixRun.getTotalCoalMwh());
            double thermal = safe(mixRun.getTotalThermalMwh());
            double wind = safe(mixRun.getWindMwh());
            double solar = safe(mixRun.getSolarMwh());
            double total = majorHydro + coal + thermal + wind + solar + safe(mixRun.getMiniHydroMwh());
            if (total <= 0) {
                total = 1;
            }

            addRow3(mixTable, "Total Generation", fmt(total) + " MWh", "100%", bodyFont);
            addRow3(mixTable, "Major Hydro", fmt(majorHydro) + " MWh", fmt(majorHydro / total * 100.0) + "%", bodyFont);
            addRow3(mixTable, "Coal", fmt(coal) + " MWh", fmt(coal / total * 100.0) + "%", bodyFont);
            addRow3(mixTable, "Thermal", fmt(thermal) + " MWh", fmt(thermal / total * 100.0) + "%", bodyFont);
            double windSolarMwh = wind + solar;
            addRow3(mixTable, "Wind & Solar", fmt(windSolarMwh) + " MWh", fmt(windSolarMwh / total * 100.0) + "%", bodyFont);
            document.add(mixTable);
            document.add(new Paragraph(" "));

            PdfPTable fuelTable = new PdfPTable(2);
            fuelTable.setWidthPercentage(100);
            addSectionHeader(fuelTable, "FUEL PRICES USED", sectionFont);
            addRow(fuelTable, "Furnace Oil (LKR)", fmt(costRun.getFoPrice()), bodyFont);
            addRow(fuelTable, "Coal (LKR)", fmt(costRun.getCoalPrice()), bodyFont);
            addRow(fuelTable, "Naphtha (LKR)", fmt(costRun.getNaphthaPrice()), bodyFont);
            addRow(fuelTable, "Diesel (LKR)", fmt(costRun.getDieselPrice()), bodyFont);
            document.add(fuelTable);
            document.add(new Paragraph(" "));

            PdfPTable costTable = new PdfPTable(2);
            costTable.setWidthPercentage(100);
            addSectionHeader(costTable, "PREDICTED UNIT COST", sectionFont);
            addRow(costTable, "Unit Cost (LKR/kWh)", fmt(costRun.getUnitCost()), bodyFont);

            double estimatedDailyDemandMwh = safe(mixRun.getEstimatedDailyDemandMwh());
            double unitCost = safe(costRun.getUnitCost());
            double totalCostMnLkr = (unitCost * estimatedDailyDemandMwh * 1000.0) / 1_000_000.0;
            addRow(costTable, "Estimated Total Cost (Mn LKR)", fmt(totalCostMnLkr), bodyFont);
            document.add(costTable);

            document.add(new Paragraph(" "));
            document.add(new Paragraph("Report Exported: " + dtf.format(LocalDateTime.now()), bodyFont));

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to export report.");
        }
    }

    private void addSectionHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setColspan(table.getNumberOfColumns());
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private void addRow(PdfPTable table, String label, String value, Font font) {
        table.addCell(new PdfPCell(new Phrase(label, font)));
        table.addCell(new PdfPCell(new Phrase(value != null ? value : "--", font)));
    }

    private void addRow3(PdfPTable table, String label, String v1, String v2, Font font) {
        table.addCell(new PdfPCell(new Phrase(label, font)));
        table.addCell(new PdfPCell(new Phrase(v1 != null ? v1 : "--", font)));
        table.addCell(new PdfPCell(new Phrase(v2 != null ? v2 : "--", font)));
    }

    private double safe(Double value) {
        return value != null && Double.isFinite(value) ? value : 0.0;
    }

    private String fmt(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return "--";
        }
        return String.format("%.2f", value);
    }

    private String fmt(double value) {
        if (!Double.isFinite(value)) {
            return "--";
        }
        return String.format("%.2f", value);
    }
}
