package com.fixgateway.controller;

import com.fixgateway.dto.TradeCaptureRequest;
import com.fixgateway.dto.TradeCaptureResponse;
import com.fixgateway.service.FixMessageService;
import com.fixgateway.util.FixMessageConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import quickfix.FieldNotFound;
import quickfix.fix44.TradeCaptureReport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequestMapping("/api/trade-capture")
@RequiredArgsConstructor
public class TradeCaptureController {

    private final FixMessageService fixMessageService;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    @PostMapping("/report")
    public ResponseEntity<TradeCaptureResponse> requestTradeCaptureReport(
            @RequestBody TradeCaptureRequest request) {
        
        try {
            log.info("Received TradeCaptureReport request: brokerId={}, tradeReportID={}, tradeRequestType={}", 
                    request.getBrokerId(), request.getTradeReportID(), request.getTradeRequestType());
            
            // 发送FIX请求
            CompletableFuture<TradeCaptureReport> future;
            if (request.getBrokerId() != null && !request.getBrokerId().isEmpty()) {
                // 使用指定的broker
                future = fixMessageService.requestTradeCaptureReport(
                    request.getBrokerId(), 
                    request.getTradeReportID(), 
                    request.getTradeRequestType()
                );
            } else {
                // 使用向后兼容的方法（默认使用第一个broker）
                future = fixMessageService.requestTradeCaptureReport(
                    request.getTradeReportID(), 
                    request.getTradeRequestType()
                );
            }
            
            // 等待响应（带超时）
            TradeCaptureReport report = future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            // 转换响应
            TradeCaptureResponse response = convertToResponse(report);
            response.setSuccess(true);
            
            log.info("TradeCaptureReport received successfully: tradeRequestID={}", 
                    response.getTradeRequestID());
            
            return ResponseEntity.ok(response);
            
        } catch (TimeoutException e) {
            log.error("Timeout waiting for TradeCaptureReport response", e);
            TradeCaptureResponse response = TradeCaptureResponse.builder()
                    .success(false)
                    .errorMessage("Timeout waiting for FIX response")
                    .build();
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
            
        } catch (Exception e) {
            log.error("Error processing TradeCaptureReport request", e);
            TradeCaptureResponse response = TradeCaptureResponse.builder()
                    .success(false)
                    .errorMessage("Error: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private TradeCaptureResponse convertToResponse(TradeCaptureReport report) throws FieldNotFound {
        Map<String, Object> reportMap = FixMessageConverter.convertTradeCaptureReportToMap(report);
        
        TradeCaptureResponse.TradeCaptureResponseBuilder builder = TradeCaptureResponse.builder();
        
        if (reportMap.containsKey("tradeRequestID")) {
            builder.tradeRequestID((String) reportMap.get("tradeRequestID"));
        }
        
        if (reportMap.containsKey("tradeReportID")) {
            builder.tradeReportID((String) reportMap.get("tradeReportID"));
        }
        
        if (reportMap.containsKey("totalNumTrades")) {
            builder.totalNumTrades((Integer) reportMap.get("totalNumTrades"));
        }
        
        if (reportMap.containsKey("fixFields")) {
            @SuppressWarnings("unchecked")
            Map<String, String> fixFields = (Map<String, String>) reportMap.get("fixFields");
            builder.fixFields(fixFields);
        }
        
        // 将单个交易信息添加到trades列表
        List<Map<String, Object>> trades = new java.util.ArrayList<>();
        Map<String, Object> tradeInfo = new HashMap<>();
        reportMap.forEach((key, value) -> {
            if (!key.equals("fixFields") && !key.equals("tradeRequestID") 
                    && !key.equals("tradeReportID") && !key.equals("totalNumTrades")) {
                tradeInfo.put(key, value);
            }
        });
        if (!tradeInfo.isEmpty()) {
            trades.add(tradeInfo);
        }
        builder.trades(trades);
        
        return builder.build();
    }
}

