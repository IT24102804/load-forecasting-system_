package com.example.loadforcasting.dto;


public class PredictionRequest {
    private String timestamp;  // ISO format
    private double temperature;
    private double humidity;
    private int public_event;

    // getters and setters
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public double getHumidity() { return humidity; }
    public void setHumidity(double humidity) { this.humidity = humidity; }
    public int getPublic_event() { return public_event; }
    public void setPublic_event(int public_event) { this.public_event = public_event; }
}