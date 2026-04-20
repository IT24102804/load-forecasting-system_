package com.example.loadforcasting.dto;

import java.util.List;

public class ForecastRequest {
    private String lastTimestamp; // starting timestamp
    private int steps;            // number of forecast steps
    private List<List<Double>> sequence; // last 3 historical records [[temp, hum, event], ...]

    // getters and setters
    public String getLastTimestamp() { return lastTimestamp; }
    public void setLastTimestamp(String lastTimestamp) { this.lastTimestamp = lastTimestamp; }
    public int getSteps() { return steps; }
    public void setSteps(int steps) { this.steps = steps; }
    public List<List<Double>> getSequence() { return sequence; }
    public void setSequence(List<List<Double>> sequence) { this.sequence = sequence; }
}
