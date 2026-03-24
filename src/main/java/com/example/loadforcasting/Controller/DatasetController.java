package com.example.loadforcasting.Controller;

import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.util.*;

@RestController
@RequestMapping("/api/dataset")
public class DatasetController {

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> result = new HashMap<>();
        List<Double> loads = new ArrayList<>();
        List<Double> temps = new ArrayList<>();
        List<String> timestamps = new ArrayList<>();

        try {
            InputStream is = getClass().getResourceAsStream("/static/dataset.csv");
            if (is == null) {
                result.put("error", "dataset.csv not found");
                return result;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            boolean firstLine = true;
            int tsCol=0, tempCol=1, loadCol=14;

            while ((line = br.readLine()) != null) {
                String[] cols = line.split(",");
                if (firstLine) {
                    for (int i = 0; i < cols.length; i++) {
                        String h = cols[i].toLowerCase().trim();
                        if (h.contains("timestamp")) tsCol = i;
                        if (h.contains("temperature")) tempCol = i;
                        if (h.contains("load")) loadCol = i;
                    }
                    firstLine = false;
                    continue;
                }
                try {
                    timestamps.add(cols[tsCol].trim());
                    temps.add(Double.parseDouble(cols[tempCol].trim()));
                    loads.add(Double.parseDouble(cols[loadCol].trim()));
                } catch (Exception e) {}
            }
            br.close();

            // Average load
            double avgLoad = 0;
            double maxLoad = 0;
            double minLoad = Double.MAX_VALUE;
            for (double l : loads) {
                avgLoad += l;
                if (l > maxLoad) maxLoad = l;
                if (l < minLoad) minLoad = l;
            }
            avgLoad = avgLoad / loads.size();

            // Last 12 records for chart
            int size = Math.min(12, loads.size());
            result.put("timestamps", timestamps.subList(timestamps.size()-size, timestamps.size()));
            result.put("loads", loads.subList(loads.size()-size, loads.size()));
            result.put("temps", temps.subList(temps.size()-size, temps.size()));
            result.put("avgLoad", Math.round(avgLoad * 10.0) / 10.0);
            result.put("maxLoad", Math.round(maxLoad * 10.0) / 10.0);
            result.put("minLoad", Math.round(minLoad * 10.0) / 10.0);
            result.put("totalRecords", loads.size());

        } catch (Exception e) {
            result.put("error", "Could not read dataset: " + e.getMessage());
        }
        return result;
    }
}
