package com.example.loadforcasting.Service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PredictionSignatureService {

    public String buildLoadSignature(LocalDateTime timestamp, double temperature, double humidity, int publicEvent) {
        return String.format(Locale.ROOT, "%s|%.4f|%.4f|%d",
                timestamp,
                temperature,
                humidity,
                publicEvent);
    }

    public String buildGenerationMixScenarioSignature(Long loadForecastRunId, double reservoirPct) {
        return String.format(Locale.ROOT, "%d|%.2f", loadForecastRunId, reservoirPct);
    }

    public String buildCostScenarioSignature(Long generationMixRunId,
                                             double foPrice,
                                             double coalPrice,
                                             double dieselPrice,
                                             double naphthaPrice) {
        return String.format(Locale.ROOT, "%d|%.2f|%.2f|%.2f|%.2f",
                generationMixRunId,
                foPrice,
                coalPrice,
                dieselPrice,
                naphthaPrice);
    }
}
