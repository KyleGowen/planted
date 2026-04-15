package com.planted;

import com.planted.config.PlantedHistoryProperties;
import com.planted.config.PlantedWeatherProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({PlantedWeatherProperties.class, PlantedHistoryProperties.class})
public class PlantedApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlantedApplication.class, args);
    }
}
