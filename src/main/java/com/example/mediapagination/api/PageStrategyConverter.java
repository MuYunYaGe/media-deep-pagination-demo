package com.example.mediapagination.api;

import com.example.mediapagination.application.model.PageStrategy;
import com.example.mediapagination.application.model.UnsupportedStrategyException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class PageStrategyConverter implements Converter<String, PageStrategy> {

    @Override
    public PageStrategy convert(String source) {
        return switch (source) {
            case "offset" -> PageStrategy.OFFSET;
            case "zset" -> PageStrategy.ZSET;
            default -> throw new UnsupportedStrategyException(source);
        };
    }
}
