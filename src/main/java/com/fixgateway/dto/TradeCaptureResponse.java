package com.fixgateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeCaptureResponse {
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 错误消息（如果失败）
     */
    private String errorMessage;
    
    /**
     * 交易请求ID
     */
    private String tradeRequestID;
    
    /**
     * 交易报告ID
     */
    private String tradeReportID;
    
    /**
     * 交易数量
     */
    private Integer totalNumTrades;
    
    /**
     * 交易详情列表
     */
    private List<Map<String, Object>> trades;
    
    /**
     * 原始FIX消息字段（键值对形式）
     */
    private Map<String, String> fixFields;
}

