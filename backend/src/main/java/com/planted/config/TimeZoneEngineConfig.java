package com.planted.config;

import net.iakovlev.timeshape.TimeZoneEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeZoneEngineConfig {

    @Bean
    public TimeZoneEngine timeZoneEngine() {
        return TimeZoneEngine.initialize();
    }
}
