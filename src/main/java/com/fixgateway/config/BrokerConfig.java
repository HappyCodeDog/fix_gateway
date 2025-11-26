package com.fixgateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * FIX Broker配置属性类
 * 支持多个broker的配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "fix")
public class BrokerConfig {

    /**
     * 默认配置（向后兼容）
     */
    private SessionConfig session;

    /**
     * 多个broker配置列表
     */
    private List<BrokerSessionConfig> brokers = new ArrayList<>();

    @Data
    public static class SessionConfig {
        private String senderCompId;
        private String targetCompId;
        private String socketConnectHost;
        private int socketConnectPort;
        private String beginString;
        private String socketUseSsl = "Y";
        private String sslProtocol = "TLSv1.2";
        private int heartbeatInterval = 30;
        private String fileLogPath = "logs";
        private String messageStoreFactory = "memory";
    }

    @Data
    public static class BrokerSessionConfig {
        /**
         * Broker唯一标识
         */
        private String brokerId;

        /**
         * 发送方CompID
         */
        private String senderCompId;

        /**
         * 接收方CompID
         */
        private String targetCompId;

        /**
         * 目标主机
         */
        private String socketConnectHost;

        /**
         * 目标端口
         */
        private int socketConnectPort;

        /**
         * FIX版本
         */
        private String beginString = "FIX.4.4";

        /**
         * 是否使用SSL
         */
        private String socketUseSsl = "Y";

        /**
         * SSL协议版本
         */
        private String sslProtocol = "TLSv1.2";

        /**
         * 心跳间隔（秒）
         */
        private int heartbeatInterval = 30;

        /**
         * 日志路径
         */
        private String fileLogPath = "logs";

        /**
         * 消息存储类型
         */
        private String messageStoreFactory = "memory";

        /**
         * 重连间隔（秒）
         */
        private int reconnectInterval = 60;

        /**
         * SSL密钥库路径
         */
        private String socketKeyStore = "";

        /**
         * SSL密钥库密码
         */
        private String socketKeyStorePassword = "";

        /**
         * SSL密钥库类型
         */
        private String socketKeyStoreType = "JKS";

        /**
         * SSL信任库路径
         */
        private String socketTrustStore = "";

        /**
         * SSL信任库密码
         */
        private String socketTrustStorePassword = "";

        /**
         * SSL信任库类型
         */
        private String socketTrustStoreType = "JKS";

        /**
         * 数据字典文件
         */
        private String dataDictionary = "FIX44.xml";
    }
}

