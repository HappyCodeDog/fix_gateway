package com.fixgateway.util;

import lombok.extern.slf4j.Slf4j;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.fix44.TradeCaptureReport;
import quickfix.field.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class FixMessageConverter {

    public static Map<String, Object> convertTradeCaptureReportToMap(TradeCaptureReport report) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 基本字段
            if (report.isSetTradeRequestID()) {
                result.put("tradeRequestID", report.getTradeRequestID().getValue());
            }
            
            if (report.isSetTradeReportID()) {
                result.put("tradeReportID", report.getTradeReportID().getValue());
            }
            
            if (report.isSetTotNumTradeReports()) {
                result.put("totalNumTrades", report.getTotNumTradeReports().getValue());
            }
            
            // 交易信息
            if (report.isSetSymbol()) {
                result.put("symbol", report.getSymbol().getValue());
            }
            
            if (report.isSetSide()) {
                result.put("side", report.getSide().getValue());
            }
            
            if (report.isSetLastQty()) {
                result.put("lastQty", report.getLastQty().getValue());
            }
            
            if (report.isSetLastPx()) {
                result.put("lastPx", report.getLastPx().getValue());
            }
            
            if (report.isSetTradeDate()) {
                result.put("tradeDate", report.getTradeDate().getValue());
            }
            
            if (report.isSetTransactTime()) {
                result.put("transactTime", report.getTransactTime().getValue());
            }
            
            if (report.isSetExecID()) {
                result.put("execID", report.getExecID().getValue());
            }
            
            if (report.isSetOrderID()) {
                result.put("orderID", report.getOrderID().getValue());
            }
            
            // 获取所有字段的键值对
            Map<String, String> allFields = new HashMap<>();
            Message.Header header = report.getHeader();
            Message.Trailer trailer = report.getTrailer();
            
            extractFields(report, allFields);
            extractFields(header, allFields);
            extractFields(trailer, allFields);
            
            result.put("fixFields", allFields);
            
        } catch (FieldNotFound e) {
            log.warn("Field not found while converting TradeCaptureReport", e);
        } catch (Exception e) {
            log.error("Error converting TradeCaptureReport to map", e);
        }
        
        return result;
    }
    
    private static void extractFields(Message message, Map<String, String> fields) {
        if (message == null) {
            return;
        }
        
        try {
            java.util.Iterator<quickfix.Field<?>> iterator = message.iterator();
            while (iterator.hasNext()) {
                quickfix.Field<?> field = iterator.next();
                fields.put(String.valueOf(field.getTag()), field.getObject().toString());
            }
        } catch (Exception e) {
            log.debug("Error extracting fields from message", e);
        }
    }
}

