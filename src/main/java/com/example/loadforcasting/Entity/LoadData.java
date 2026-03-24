package com.example.loadforcasting.Entity;

import jakarta.persistence.*;

@Entity
@Table(name = "load_data")
public class LoadData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String timestamp;
    private double temperature;
    private double humidity;
    private double windSpeed;
    private double rainfall;
    private double solarIrradiance;
    private double gdp;
    private double perCapitaEnergy;
    private double electricityPrice;
    private int dayOfWeek;
    private int hourOfDay;
    private int month;
    private String season;
    private int publicEvent;
    private double loadDemand;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public double getHumidity() { return humidity; }
    public void setHumidity(double humidity) { this.humidity = humidity; }
    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }
    public double getRainfall() { return rainfall; }
    public void setRainfall(double rainfall) { this.rainfall = rainfall; }
    public double getSolarIrradiance() { return solarIrradiance; }
    public void setSolarIrradiance(double solarIrradiance) { this.solarIrradiance = solarIrradiance; }
    public double getGdp() { return gdp; }
    public void setGdp(double gdp) { this.gdp = gdp; }
    public double getPerCapitaEnergy() { return perCapitaEnergy; }
    public void setPerCapitaEnergy(double perCapitaEnergy) { this.perCapitaEnergy = perCapitaEnergy; }
    public double getElectricityPrice() { return electricityPrice; }
    public void setElectricityPrice(double electricityPrice) { this.electricityPrice = electricityPrice; }
    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public int getHourOfDay() { return hourOfDay; }
    public void setHourOfDay(int hourOfDay) { this.hourOfDay = hourOfDay; }
    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }
    public int getPublicEvent() { return publicEvent; }
    public void setPublicEvent(int publicEvent) { this.publicEvent = publicEvent; }
    public double getLoadDemand() { return loadDemand; }
    public void setLoadDemand(double loadDemand) { this.loadDemand = loadDemand; }
}
