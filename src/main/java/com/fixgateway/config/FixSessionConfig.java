package com.fixgateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import quickfix.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.InputStream;
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
     * 存储已加载的配置文件，key为配置文件路径，value为SessionSettings
     * 用于支持多个broker共享同一个配置文件
     */
    private final Map<String, SessionSettings> loadedConfigFiles = new HashMap<>();
    
    /**
     * 存储配置文件对应的initiator，key为配置文件路径，value为SocketInitiator
     * 用于支持多个broker共享同一个配置文件和initiator
     */
    private final Map<String, SocketInitiator> configFileInitiators = new HashMap<>();

    /**
     * 从QuickFixJ配置文件加载SessionSettings
     * 支持缓存，如果配置文件已加载过，直接返回缓存的SessionSettings
     * @param configFile 配置文件路径（相对于classpath或绝对路径）
     * @return SessionSettings
     */
    private SessionSettings loadSessionSettingsFromFile(String configFile) throws ConfigError {
        // 如果已经加载过，直接返回缓存的配置
        if (loadedConfigFiles.containsKey(configFile)) {
            log.debug("Using cached SessionSettings for configuration file: {}", configFile);
            return loadedConfigFiles.get(configFile);
        }
        
        try {
            InputStream inputStream = null;
            
            // 首先尝试从classpath加载
            inputStream = getClass().getClassLoader().getResourceAsStream(configFile);
            
            // 如果classpath中没有，尝试作为绝对路径
            if (inputStream == null) {
                java.io.File file = new java.io.File(configFile);
                if (file.exists()) {
                    inputStream = new java.io.FileInputStream(file);
                }
            }
            
            if (inputStream == null) {
                throw new ConfigError("Configuration file not found: " + configFile);
            }
            
            SessionSettings settings = new SessionSettings(inputStream);
            inputStream.close();
            
            // 缓存配置
            loadedConfigFiles.put(configFile, settings);
            
            log.info("Loaded SessionSettings from configuration file: {}", configFile);
            return settings;
        } catch (java.io.IOException e) {
            throw new ConfigError("Error reading configuration file: " + configFile, e);
        }
    }
    
    /**
     * 从SessionSettings中根据SessionID信息查找匹配的SessionID
     * @param settings SessionSettings对象
     * @param beginString BeginString（可选，如果为null则匹配任意）
     * @param senderCompId SenderCompID（可选，如果为null则匹配任意）
     * @param targetCompId TargetCompID（可选，如果为null则匹配任意）
     * @return 匹配的SessionID，如果未找到则返回null
     */
    private SessionID findSessionIdInSettings(SessionSettings settings, 
                                               String beginString, 
                                               String senderCompId, 
                                               String targetCompId) {
        java.util.Iterator<SessionID> sessionIterator = settings.sectionIterator();
        while (sessionIterator.hasNext()) {
            SessionID sessionId = sessionIterator.next();
            
            // 如果所有参数都匹配，或者参数为null（表示不限制）
            boolean beginStringMatch = beginString == null || beginString.equals(sessionId.getBeginString());
            boolean senderMatch = senderCompId == null || senderCompId.equals(sessionId.getSenderCompID());
            boolean targetMatch = targetCompId == null || targetCompId.equals(sessionId.getTargetCompID());
            
            if (beginStringMatch && senderMatch && targetMatch) {
                return sessionId;
            }
        }
        return null;
    }

    /**
     * 为指定的broker配置创建SessionSettings（向后兼容，如果未指定configFile则使用此方法）
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
                
                // 优先使用QuickFixJ配置文件
                SessionSettings settings;
                SessionID sessionId;
                
                if (brokerSessionConfig.getConfigFile() != null && !brokerSessionConfig.getConfigFile().isEmpty()) {
                    // 从配置文件加载（支持缓存，多个broker可以共享同一个配置文件）
                    settings = loadSessionSettingsFromFile(brokerSessionConfig.getConfigFile());
                    
                    // 从配置文件中查找匹配的SessionID
                    // 如果配置了SessionID信息，则精确匹配；否则使用第一个可用的SessionID
                    sessionId = null;
                    if (brokerSessionConfig.getBeginString() != null && 
                        brokerSessionConfig.getSenderCompId() != null && 
                        brokerSessionConfig.getTargetCompId() != null) {
                        // 精确匹配指定的SessionID
                        sessionId = findSessionIdInSettings(
                            settings,
                            brokerSessionConfig.getBeginString(),
                            brokerSessionConfig.getSenderCompId(),
                            brokerSessionConfig.getTargetCompId()
                        );
                        if (sessionId == null) {
                            throw new ConfigError(String.format(
                                "Session not found in configuration file %s with BeginString=%s, SenderCompID=%s, TargetCompID=%s",
                                brokerSessionConfig.getConfigFile(),
                                brokerSessionConfig.getBeginString(),
                                brokerSessionConfig.getSenderCompId(),
                                brokerSessionConfig.getTargetCompId()
                            ));
                        }
                        log.info("Matched SessionID from configuration file for broker {}: {}", brokerId, sessionId);
                    } else {
                        // 如果没有指定SessionID信息，使用第一个可用的SessionID
                        java.util.Iterator<SessionID> sessionIterator = settings.sectionIterator();
                        if (sessionIterator.hasNext()) {
                            sessionId = sessionIterator.next();
                            log.info("Using first available SessionID from configuration file for broker {}: {}", brokerId, sessionId);
                        } else {
                            throw new ConfigError("No session found in configuration file: " + brokerSessionConfig.getConfigFile());
                        }
                    }
                } else {
                    // 使用代码方式创建配置（向后兼容）
                    settings = createSessionSettings(brokerSessionConfig);
                    sessionId = new SessionID(
                        brokerSessionConfig.getBeginString(),
                        brokerSessionConfig.getSenderCompId(),
                        brokerSessionConfig.getTargetCompId()
                    );
                }
                
                // 如果使用配置文件，检查是否已经有该配置文件的initiator
                SocketInitiator initiator = null;
                if (brokerSessionConfig.getConfigFile() != null && !brokerSessionConfig.getConfigFile().isEmpty()) {
                    // 如果该配置文件已经有initiator，则复用
                    initiator = configFileInitiators.get(brokerSessionConfig.getConfigFile());
                }
                
                if (initiator == null) {
                    // 创建新的initiator
                    MessageStoreFactory messageStoreFactory = new MemoryStoreFactory();
                    LogFactory logFactory = new FileLogFactory(settings);
                    MessageFactory messageFactory = new DefaultMessageFactory();
                    
                    initiator = new SocketInitiator(
                        fixApplication, 
                        messageStoreFactory, 
                        settings, 
                        logFactory, 
                        messageFactory
                    );
                    
                    initiator.start();
                    
                    // 如果使用配置文件，缓存initiator以便其他broker复用
                    if (brokerSessionConfig.getConfigFile() != null && !brokerSessionConfig.getConfigFile().isEmpty()) {
                        configFileInitiators.put(brokerSessionConfig.getConfigFile(), initiator);
                        log.info("Created and cached SocketInitiator for configuration file: {}", brokerSessionConfig.getConfigFile());
                    }
                } else {
                    log.info("Reusing existing SocketInitiator for configuration file: {}", brokerSessionConfig.getConfigFile());
                }
                
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
        
        // 停止所有唯一的initiator（避免重复停止共享的initiator）
        for (SocketInitiator initiator : configFileInitiators.values()) {
            try {
                log.info("Stopping shared SocketInitiator");
                initiator.stop();
                log.info("Shared SocketInitiator stopped");
            } catch (Exception e) {
                log.error("Error stopping shared SocketInitiator", e);
            }
        }
        
        // 停止独立的initiator（不使用配置文件的）
        for (Map.Entry<String, SocketInitiator> entry : initiators.entrySet()) {
            String brokerId = entry.getKey();
            SocketInitiator initiator = entry.getValue();
            // 如果这个initiator不在configFileInitiators中，说明是独立的，需要单独停止
            if (!configFileInitiators.containsValue(initiator)) {
                try {
                    log.info("Stopping FIX Initiator for broker: {}", brokerId);
                    initiator.stop();
                    log.info("FIX Initiator stopped for broker: {}", brokerId);
                } catch (Exception e) {
                    log.error("Error stopping FIX Initiator for broker: {}", brokerId, e);
                }
            }
        }
        
        initiators.clear();
        sessionIds.clear();
        loadedConfigFiles.clear();
        configFileInitiators.clear();
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

