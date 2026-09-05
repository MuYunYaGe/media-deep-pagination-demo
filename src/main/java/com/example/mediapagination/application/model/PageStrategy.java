package com.example.mediapagination.application.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PageStrategy {
    OFFSET,
    ZSET;

    @JsonValue
    public String jsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
