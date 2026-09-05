package com.example.mediapagination.config;

import com.example.mediapagination.application.query.PageWindow;
import com.example.mediapagination.application.query.MediaOrderer;
import com.example.mediapagination.application.port.Sleeper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

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

    @Bean
    public MediaOrderer mediaOrderer() {
        return new MediaOrderer();
    }

    @Bean
    public Sleeper sleeper() {
        return duration -> TimeUnit.NANOSECONDS.sleep(duration.toNanos());
    }
}
