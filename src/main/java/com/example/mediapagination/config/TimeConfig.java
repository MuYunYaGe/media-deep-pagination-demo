package com.example.mediapagination.config;

import com.example.mediapagination.application.query.PageWindow;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PageWindow pageWindow(PaginationProperties properties) {
        return new PageWindow(properties.getWindowSize());
    }
}
