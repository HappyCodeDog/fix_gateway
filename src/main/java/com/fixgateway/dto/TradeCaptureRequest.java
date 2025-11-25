package com.fixgateway.dto;

import lombok.Data;

@Data
public class TradeCaptureRequest {
    /**
     * 交易报告ID（可选）
     */
    private String tradeReportID;
    
    /**
     * 交易请求类型（可选，默认为ALL_TRADES）
     */
    private String tradeRequestType;
}

