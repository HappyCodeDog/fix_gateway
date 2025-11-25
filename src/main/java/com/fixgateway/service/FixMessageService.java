package com.fixgateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import quickfix.*;
import quickfix.fix44.TradeCaptureReport;
import quickfix.fix44.TradeCaptureReportRequest;
import quickfix.field.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixMessageService {

    private final FixApplication fixApplication;
    private final SessionID sessionID;

    public CompletableFuture<TradeCaptureReport> requestTradeCaptureReport(String tradeReportID, String tradeRequestType) {
        try {
            // 生成唯一的请求ID
            String tradeRequestID = UUID.randomUUID().toString();
            
            // 创建TradeCaptureReportRequest消息
            TradeCaptureReportRequest request = new TradeCaptureReportRequest();
            request.set(new TradeRequestID(tradeRequestID));
            request.set(new TradeRequestType(TradeRequestType.ALL_TRADES));
            
            if (tradeReportID != null && !tradeReportID.isEmpty()) {
                request.set(new TradeReportID(tradeReportID));
            }
            
            // 注册响应等待
            CompletableFuture<TradeCaptureReport> future = fixApplication.registerRequest(tradeRequestID);
            
            // 发送请求
            Session.sendToTarget(request, sessionID);
            log.info("Sent TradeCaptureReportRequest with TradeRequestID: {}", tradeRequestID);
            
            return future;
        } catch (SessionNotFound e) {
            log.error("Session not found: {}", sessionID, e);
            CompletableFuture<TradeCaptureReport> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("FIX session not found", e));
            return failed;
        } catch (Exception e) {
            log.error("Error sending TradeCaptureReportRequest", e);
            CompletableFuture<TradeCaptureReport> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    public CompletableFuture<TradeCaptureReport> requestTradeCaptureReport(String tradeReportID) {
        return requestTradeCaptureReport(tradeReportID, null);
    }
}

