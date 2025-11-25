package com.fixgateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import quickfix.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Slf4j
@Configuration
public class FixSessionConfig {

    @Value("${fix.session.sender-comp-id}")
    private String senderCompId;

    @Value("${fix.session.target-comp-id}")
    private String targetCompId;

    @Value("${fix.session.socket-connect-host}")
    private String socketConnectHost;

    @Value("${fix.session.socket-connect-port}")
    private int socketConnectPort;

    @Value("${fix.session.begin-string}")
    private String beginString;

    @Value("${fix.session.socket-use-ssl:Y}")
    private String socketUseSsl;

    @Value("${fix.session.ssl-protocol:TLSv1.2}")
    private String sslProtocol;

    @Value("${fix.session.heartbeat-interval:30}")
    private int heartbeatInterval;

    @Value("${fix.session.file-log-path:logs}")
    private String fileLogPath;

    @Value("${fix.session.message-store-factory:memory}")
    private String messageStoreFactory;

    private SocketInitiator initiator;
    
    @Autowired
    private com.fixgateway.service.FixApplication fixApplication;

    @Bean
    public SessionID sessionID() {
        return new SessionID(beginString, senderCompId, targetCompId);
    }

    @Bean
    public SessionSettings sessionSettings() throws ConfigError {
        SessionSettings settings = new SessionSettings();
        
        // 创建默认会话配置
        Dictionary defaultSettings = new Dictionary();
        defaultSettings.setString("ConnectionType", "initiator");
        defaultSettings.setString("ReconnectInterval", "60");
        defaultSettings.setString("FileStorePath", fileLogPath);
        defaultSettings.setString("FileLogPath", fileLogPath);
        defaultSettings.setString("StartTime", "00:00:00");
        defaultSettings.setString("EndTime", "00:00:00");
        defaultSettings.setString("HeartBtInt", String.valueOf(heartbeatInterval));
        defaultSettings.setString("SocketUseSSL", socketUseSsl);
        if ("Y".equals(socketUseSsl)) {
            defaultSettings.setString("SocketKeyStore", "");
            defaultSettings.setString("SocketKeyStorePassword", "");
            defaultSettings.setString("SocketKeyStoreType", "JKS");
            defaultSettings.setString("SocketTrustStore", "");
            defaultSettings.setString("SocketTrustStorePassword", "");
            defaultSettings.setString("SocketTrustStoreType", "JKS");
        }
        
        settings.set(defaultSettings);
        
        // 创建会话配置
        Dictionary sessionDict = new Dictionary();
        sessionDict.setString("BeginString", beginString);
        sessionDict.setString("SenderCompID", senderCompId);
        sessionDict.setString("TargetCompID", targetCompId);
        sessionDict.setString("SocketConnectHost", socketConnectHost);
        sessionDict.setString("SocketConnectPort", String.valueOf(socketConnectPort));
        sessionDict.setString("DataDictionary", "FIX44.xml");
        
        SessionID sessionId = new SessionID(beginString, senderCompId, targetCompId);
        settings.set(sessionId, sessionDict);
        
        return settings;
    }

    @PostConstruct
    public void startInitiator() throws Exception {
        try {
            SessionSettings settings = sessionSettings();
            MessageStoreFactory messageStoreFactory = new MemoryStoreFactory();
            LogFactory logFactory = new FileLogFactory(settings);
            MessageFactory messageFactory = new DefaultMessageFactory();
            
            initiator = new SocketInitiator(fixApplication, messageStoreFactory, 
                    settings, logFactory, messageFactory);
            
            initiator.start();
            log.info("FIX Initiator started successfully");
        } catch (Exception e) {
            log.error("Failed to start FIX Initiator", e);
            throw e;
        }
    }

    @PreDestroy
    public void stopInitiator() {
        if (initiator != null) {
            log.info("Stopping FIX Initiator...");
            initiator.stop();
            log.info("FIX Initiator stopped");
        }
    }

    public SocketInitiator getInitiator() {
        return initiator;
    }
}

