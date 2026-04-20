package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.LoadData;
import com.example.loadforcasting.Repository.LoadDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.util.*;

@RestController
@RequestMapping("/api/loaddata")
public class LoadDataController {

    @Autowired
    private LoadDataRepository loadDataRepository;

    // Import CSV into database
    @GetMapping("/import")
    public String importCSV() {
        try {
            InputStream is = getClass().getResourceAsStream("/static/dataset.csv");
            if (is == null) {
                return "Error: dataset.csv not found in static folder!";
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            boolean firstLine = true;
            int count = 0;

            while ((line = br.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }
                String[] cols = line.split(",");
                if (cols.length < 15) continue;
                try {
                    LoadData d = new LoadData();
                    d.setTimestamp(cols[0].trim());
                    d.setTemperature(Double.parseDouble(cols[1].trim()));
                    d.setHumidity(Double.parseDouble(cols[2].trim()));
                    d.setWindSpeed(Double.parseDouble(cols[3].trim()));
                    d.setRainfall(Double.parseDouble(cols[4].trim()));
                    d.setSolarIrradiance(Double.parseDouble(cols[5].trim()));
                    d.setGdp(Double.parseDouble(cols[6].trim()));
                    d.setPerCapitaEnergy(Double.parseDouble(cols[7].trim()));
                    d.setElectricityPrice(Double.parseDouble(cols[8].trim()));
                    d.setDayOfWeek(Integer.parseInt(cols[9].trim()));
                    d.setHourOfDay(Integer.parseInt(cols[10].trim()));
                    d.setMonth(Integer.parseInt(cols[11].trim()));
                    d.setSeason(cols[12].trim());
                    d.setPublicEvent(Integer.parseInt(cols[13].trim()));
                    d.setLoadDemand(Double.parseDouble(cols[14].trim()));
                    loadDataRepository.save(d);
                    count++;
                } catch (Exception e) {}
            }
            br.close();
            return "Imported " + count + " records successfully!";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Get stats for monitoring page
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> result = new HashMap<>();
        List<LoadData> allData = loadDataRepository.findAll();

        if (allData.isEmpty()) {
            result.put("error", "No data found. Please call /api/loaddata/import first.");
            return result;
        }

        List<String> timestamps = new ArrayList<>();
        List<Double> loads = new ArrayList<>();
        List<Double> temps = new ArrayList<>();
        double totalLoad = 0, maxLoad = 0, minLoad = Double.MAX_VALUE;

        for (LoadData d : allData) {
            totalLoad += d.getLoadDemand();
            if (d.getLoadDemand() > maxLoad) maxLoad = d.getLoadDemand();
            if (d.getLoadDemand() < minLoad) minLoad = d.getLoadDemand();
        }

        // Last 12 records for chart
        int size = Math.min(12, allData.size());
        List<LoadData> recent = allData.subList(allData.size() - size, allData.size());
        for (LoadData d : recent) {
            timestamps.add(d.getTimestamp());
            loads.add(d.getLoadDemand());
            temps.add(d.getTemperature());
        }

        result.put("timestamps", timestamps);
        result.put("loads", loads);
        result.put("temps", temps);
        result.put("avgLoad", Math.round((totalLoad / allData.size()) * 10.0) / 10.0);
        result.put("maxLoad", Math.round(maxLoad * 10.0) / 10.0);
        result.put("minLoad", Math.round(minLoad * 10.0) / 10.0);
        result.put("totalRecords", allData.size());

        return result;
    }

    // Delete outdated data
    @DeleteMapping("/delete-old")
    public String deleteOldData(@RequestParam String cutoffDate) {
        try {
            loadDataRepository.deleteOldData(java.time.LocalDateTime.parse(cutoffDate));
            return "Outdated data deleted successfully!";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
