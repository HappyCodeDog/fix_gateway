package com.fixgateway.exception;

import com.fixgateway.dto.TradeCaptureResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TradeCaptureResponse> handleException(Exception e) {
        log.error("Unhandled exception", e);
        TradeCaptureResponse response = TradeCaptureResponse.builder()
                .success(false)
                .errorMessage("Internal server error: " + e.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

