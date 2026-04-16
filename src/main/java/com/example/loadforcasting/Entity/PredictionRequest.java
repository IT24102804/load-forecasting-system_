package com.example.loadforcasting.Entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PredictionRequest {

    @NotBlank(message = "Date and time is required")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$",
            message = "Invalid format. Use YYYY-MM-DDTHH:MM (e.g., 2026-03-27T14:30)")
    private String dateTime;

    public PredictionRequest() {
    }

    public PredictionRequest(String dateTime) {
        this.dateTime = dateTime;
    }

    public String getDateTime() {
        return dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }
}