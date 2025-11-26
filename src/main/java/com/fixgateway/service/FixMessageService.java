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
    private final com.fixgateway.config.FixSessionConfig fixSessionConfig;

    /**
     * 根据brokerId发送TradeCaptureReportRequest
     * @param brokerId broker标识
     * @param tradeReportID 交易报告ID
     * @param tradeRequestType 交易请求类型
     * @return CompletableFuture<TradeCaptureReport>
     */
    public CompletableFuture<TradeCaptureReport> requestTradeCaptureReport(
            String brokerId, String tradeReportID, String tradeRequestType) {
        try {
            SessionID sessionID = fixSessionConfig.getSessionId(brokerId);
            if (sessionID == null) {
                log.error("Session not found for broker: {}", brokerId);
                CompletableFuture<TradeCaptureReport> failed = new CompletableFuture<>();
                failed.completeExceptionally(new RuntimeException("FIX session not found for broker: " + brokerId));
                return failed;
            }

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
            log.info("Sent TradeCaptureReportRequest to broker {} with TradeRequestID: {}", brokerId, tradeRequestID);
            
            return future;
        } catch (SessionNotFound e) {
            log.error("Session not found for broker: {}", brokerId, e);
            CompletableFuture<TradeCaptureReport> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("FIX session not found for broker: " + brokerId, e));
            return failed;
        } catch (Exception e) {
            log.error("Error sending TradeCaptureReportRequest to broker: {}", brokerId, e);
            CompletableFuture<TradeCaptureReport> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    /**
     * 根据brokerId发送TradeCaptureReportRequest（使用默认请求类型）
     * @param brokerId broker标识
     * @param tradeReportID 交易报告ID
     * @return CompletableFuture<TradeCaptureReport>
     */
    public CompletableFuture<TradeCaptureReport> requestTradeCaptureReport(String brokerId, String tradeReportID) {
        return requestTradeCaptureReport(brokerId, tradeReportID, null);
    }

    /**
     * 使用第一个可用的broker发送TradeCaptureReportRequest（向后兼容）
     * @param tradeReportID 交易报告ID
     * @param tradeRequestType 交易请求类型
     * @return CompletableFuture<TradeCaptureReport>
     */
    public CompletableFuture<TradeCaptureReport> requestTradeCaptureReport(String tradeReportID, String tradeRequestType) {
        List<String> brokerIds = fixSessionConfig.getBrokerIds();
        if (brokerIds == null || brokerIds.isEmpty()) {
            CompletableFuture<TradeCaptureReport> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("No broker configured"));
            return failed;
        }
        // 使用第一个broker
        String defaultBrokerId = brokerIds.get(0);
        log.info("Using default broker: {} for backward compatibility", defaultBrokerId);
        return requestTradeCaptureReport(defaultBrokerId, tradeReportID, tradeRequestType);
    }
}

