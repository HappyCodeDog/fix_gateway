package com.fixgateway.dto;

import lombok.Data;

@Data
public class TradeCaptureRequest {
    /**
     * Broker标识（可选，如果不指定则使用第一个可用的broker）
     */
    private String brokerId;
    
    /**
     * 交易报告ID（可选）
     */
    private String tradeReportID;
    
    /**
     * 交易请求类型（可选，默认为ALL_TRADES）
     */
    private String tradeRequestType;
}

