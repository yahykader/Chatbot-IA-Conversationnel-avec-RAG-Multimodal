package com.exemple.transactionservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix="antivirus")
public class ClamAvProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 3310;
    private int timeout = 30000;
    private int chunkSize = 2048;
    private long maxFileSize = 104857600;

    private HealthCheck healthCheck = new HealthCheck();

    @Getter
    @Setter
    public static class HealthCheck {
        private boolean enabled = true;
        private long interval = 60000;
    }
}
