package com.fixgateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.fix44.TradeCaptureReport;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FixApplication implements Application {

    private final Map<String, CompletableFuture<TradeCaptureReport>> pendingRequests = new ConcurrentHashMap<>();

    @Override
    public void onCreate(SessionID sessionId) {
        log.info("Session created: {}", sessionId);
    }

    @Override
    public void onLogon(SessionID sessionId) {
        log.info("Session logged on: {}", sessionId);
    }

    @Override
    public void onLogout(SessionID sessionId) {
        log.info("Session logged out: {}", sessionId);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        log.debug("Sending admin message: {}", message);
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        log.debug("Received admin message: {}", message);
    }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        log.debug("Sending app message: {}", message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        log.info("Received app message: {}", message);
        
        if (message instanceof TradeCaptureReport) {
            handleTradeCaptureReport((TradeCaptureReport) message);
        }
    }

    private void handleTradeCaptureReport(TradeCaptureReport report) {
        try {
            String tradeRequestID = report.isSetTradeRequestID() ? report.getTradeRequestID().getValue() : null;
            
            if (tradeRequestID != null && pendingRequests.containsKey(tradeRequestID)) {
                CompletableFuture<TradeCaptureReport> future = pendingRequests.remove(tradeRequestID);
                future.complete(report);
                log.info("TradeCaptureReport received for request ID: {}", tradeRequestID);
            } else {
                log.warn("Received TradeCaptureReport with unknown or missing TradeRequestID");
            }
        } catch (Exception e) {
            log.error("Error handling TradeCaptureReport", e);
        }
    }

    public CompletableFuture<TradeCaptureReport> registerRequest(String tradeRequestID) {
        CompletableFuture<TradeCaptureReport> future = new CompletableFuture<>();
        pendingRequests.put(tradeRequestID, future);
        return future;
    }

    public void removeRequest(String tradeRequestID) {
        pendingRequests.remove(tradeRequestID);
    }
}

