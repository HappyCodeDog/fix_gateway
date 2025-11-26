package com.fixgateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import quickfix.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class FixSessionConfig {

    @Autowired
    private BrokerConfig brokerConfig;
    
    @Autowired
    private com.fixgateway.service.FixApplication fixApplication;

    /**
     * 存储每个broker的initiator，key为brokerId
     */
    private final Map<String, SocketInitiator> initiators = new HashMap<>();
    
    /**
     * 存储每个broker的SessionID，key为brokerId
     */
    private final Map<String, SessionID> sessionIds = new HashMap<>();

    /**
     * 为指定的broker配置创建SessionSettings
     */
    private SessionSettings createSessionSettings(BrokerConfig.BrokerSessionConfig brokerConfig) throws ConfigError {
        SessionSettings settings = new SessionSettings();
        
        // 创建默认会话配置
        Dictionary defaultSettings = new Dictionary();
        defaultSettings.setString("ConnectionType", "initiator");
        defaultSettings.setString("ReconnectInterval", String.valueOf(brokerConfig.getReconnectInterval()));
        defaultSettings.setString("FileStorePath", brokerConfig.getFileLogPath());
        defaultSettings.setString("FileLogPath", brokerConfig.getFileLogPath());
        defaultSettings.setString("StartTime", "00:00:00");
        defaultSettings.setString("EndTime", "00:00:00");
        defaultSettings.setString("HeartBtInt", String.valueOf(brokerConfig.getHeartbeatInterval()));
        defaultSettings.setString("SocketUseSSL", brokerConfig.getSocketUseSsl());
        
        if ("Y".equals(brokerConfig.getSocketUseSsl())) {
            defaultSettings.setString("SocketKeyStore", brokerConfig.getSocketKeyStore());
            defaultSettings.setString("SocketKeyStorePassword", brokerConfig.getSocketKeyStorePassword());
            defaultSettings.setString("SocketKeyStoreType", brokerConfig.getSocketKeyStoreType());
            defaultSettings.setString("SocketTrustStore", brokerConfig.getSocketTrustStore());
            defaultSettings.setString("SocketTrustStorePassword", brokerConfig.getSocketTrustStorePassword());
            defaultSettings.setString("SocketTrustStoreType", brokerConfig.getSocketTrustStoreType());
        }
        
        settings.set(defaultSettings);
        
        // 创建会话配置
        Dictionary sessionDict = new Dictionary();
        sessionDict.setString("BeginString", brokerConfig.getBeginString());
        sessionDict.setString("SenderCompID", brokerConfig.getSenderCompId());
        sessionDict.setString("TargetCompID", brokerConfig.getTargetCompId());
        sessionDict.setString("SocketConnectHost", brokerConfig.getSocketConnectHost());
        sessionDict.setString("SocketConnectPort", String.valueOf(brokerConfig.getSocketConnectPort()));
        sessionDict.setString("DataDictionary", brokerConfig.getDataDictionary());
        
        SessionID sessionId = new SessionID(
            brokerConfig.getBeginString(), 
            brokerConfig.getSenderCompId(), 
            brokerConfig.getTargetCompId()
        );
        settings.set(sessionId, sessionDict);
        
        return settings;
    }

    /**
     * 从旧的单个session配置创建BrokerSessionConfig（向后兼容）
     */
    private BrokerConfig.BrokerSessionConfig createBrokerConfigFromLegacy() {
        BrokerConfig.SessionConfig legacyConfig = brokerConfig.getSession();
        if (legacyConfig == null) {
            return null;
        }
        
        BrokerConfig.BrokerSessionConfig brokerSessionConfig = new BrokerConfig.BrokerSessionConfig();
        brokerSessionConfig.setBrokerId("default");
        brokerSessionConfig.setSenderCompId(legacyConfig.getSenderCompId());
        brokerSessionConfig.setTargetCompId(legacyConfig.getTargetCompId());
        brokerSessionConfig.setSocketConnectHost(legacyConfig.getSocketConnectHost());
        brokerSessionConfig.setSocketConnectPort(legacyConfig.getSocketConnectPort());
        brokerSessionConfig.setBeginString(legacyConfig.getBeginString());
        brokerSessionConfig.setSocketUseSsl(legacyConfig.getSocketUseSsl());
        brokerSessionConfig.setSslProtocol(legacyConfig.getSslProtocol());
        brokerSessionConfig.setHeartbeatInterval(legacyConfig.getHeartbeatInterval());
        brokerSessionConfig.setFileLogPath(legacyConfig.getFileLogPath());
        brokerSessionConfig.setMessageStoreFactory(legacyConfig.getMessageStoreFactory());
        
        return brokerSessionConfig;
    }

    @PostConstruct
    public void startInitiators() throws Exception {
        List<BrokerConfig.BrokerSessionConfig> brokerConfigs = brokerConfig.getBrokers();
        
        // 如果brokers列表为空，尝试使用旧的单个session配置（向后兼容）
        if (brokerConfigs == null || brokerConfigs.isEmpty()) {
            BrokerConfig.BrokerSessionConfig legacyConfig = createBrokerConfigFromLegacy();
            if (legacyConfig != null) {
                log.info("No brokers configured, using legacy session configuration");
                brokerConfigs = List.of(legacyConfig);
            } else {
                log.warn("No broker configurations found. Please configure at least one broker in application.yml");
                return;
            }
        }
        
        // 为每个broker创建并启动initiator
        for (BrokerConfig.BrokerSessionConfig brokerSessionConfig : brokerConfigs) {
            try {
                String brokerId = brokerSessionConfig.getBrokerId();
                if (brokerId == null || brokerId.isEmpty()) {
                    log.warn("Broker configuration missing broker-id, skipping...");
                    continue;
                }
                
                log.info("Starting FIX Initiator for broker: {}", brokerId);
                
                SessionSettings settings = createSessionSettings(brokerSessionConfig);
                MessageStoreFactory messageStoreFactory = new MemoryStoreFactory();
                LogFactory logFactory = new FileLogFactory(settings);
                MessageFactory messageFactory = new DefaultMessageFactory();
                
                SocketInitiator initiator = new SocketInitiator(
                    fixApplication, 
                    messageStoreFactory, 
                    settings, 
                    logFactory, 
                    messageFactory
                );
                
                initiator.start();
                
                // 保存initiator和对应的SessionID
                SessionID sessionId = new SessionID(
                    brokerSessionConfig.getBeginString(),
                    brokerSessionConfig.getSenderCompId(),
                    brokerSessionConfig.getTargetCompId()
                );
                
                initiators.put(brokerId, initiator);
                sessionIds.put(brokerId, sessionId);
                
                log.info("FIX Initiator started successfully for broker: {} (SessionID: {})", 
                    brokerId, sessionId);
            } catch (Exception e) {
                log.error("Failed to start FIX Initiator for broker: {}", 
                    brokerSessionConfig.getBrokerId(), e);
                // 继续启动其他broker，不中断整个流程
            }
        }
        
        log.info("Total {} FIX Initiator(s) started", initiators.size());
    }

    @PreDestroy
    public void stopInitiators() {
        log.info("Stopping all FIX Initiators...");
        for (Map.Entry<String, SocketInitiator> entry : initiators.entrySet()) {
            try {
                String brokerId = entry.getKey();
                SocketInitiator initiator = entry.getValue();
                log.info("Stopping FIX Initiator for broker: {}", brokerId);
                initiator.stop();
                log.info("FIX Initiator stopped for broker: {}", brokerId);
            } catch (Exception e) {
                log.error("Error stopping FIX Initiator for broker: {}", entry.getKey(), e);
            }
        }
        initiators.clear();
        sessionIds.clear();
        log.info("All FIX Initiators stopped");
    }

    /**
     * 根据brokerId获取对应的SocketInitiator
     */
    public SocketInitiator getInitiator(String brokerId) {
        return initiators.get(brokerId);
    }

    /**
     * 根据brokerId获取对应的SessionID
     */
    public SessionID getSessionId(String brokerId) {
        return sessionIds.get(brokerId);
    }

    /**
     * 获取所有已配置的brokerId列表
     */
    public List<String> getBrokerIds() {
        return initiators.keySet().stream().collect(Collectors.toList());
    }

    /**
     * 获取所有initiator的映射
     */
    public Map<String, SocketInitiator> getAllInitiators() {
        return new HashMap<>(initiators);
    }

    /**
     * 获取所有SessionID的映射
     */
    public Map<String, SessionID> getAllSessionIds() {
        return new HashMap<>(sessionIds);
    }
}

