package com.example.mediapagination.api;

import com.example.mediapagination.application.model.PageOutsideWindowException;
import com.example.mediapagination.application.model.UnsupportedStrategyException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PageOutsideWindowException.class)
    public ResponseEntity<ApiError> pageOutsideWindow(PageOutsideWindowException exception) {
        return error(HttpStatus.BAD_REQUEST, "PAGE_OUTSIDE_CACHE_WINDOW",
                exception.getMessage());
    }

    @ExceptionHandler({ConstraintViolationException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            UnsupportedStrategyException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiError> invalidRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PAGE_REQUEST",
                "Invalid pagination request");
    }

    private static ResponseEntity<ApiError> error(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, MDC.get("traceId")));
    }
}
