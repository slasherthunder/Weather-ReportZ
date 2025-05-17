package com.example.weather;

public class WeatherResponse {
    private Main main;
//    private List<Weather> weather;
    private String name;

    // Getters and setters

    public class Main {
        private double temp;
        private double humidity;

        // Getters and setters
    }

    public class Weather {
        private String main;
        private String description;
        private String icon;

        // Getters and setters
    }
}