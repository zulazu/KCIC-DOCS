package com.kcic.connect.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kcic")
public record AppProperties(App app, Security security, Storage storage, Files files) {

    public record App(String name, String version, String baseUrl, String requestIdHeader) {
    }

    public record Security(
            boolean cookieSecure,
            int passwordMinLength,
            int loginMaxFailures,
            int loginLockMinutes,
            Bootstrap bootstrap
    ) {
    }

    public record Bootstrap(String username, String password, String email, String displayName,
                            String employeeNip) {
    }

    public record Storage(String provider, String endpoint, String accessKey, String secretKey, String bucket,
                          String region, String localRoot) {
    }

    public record Files(long defaultMaxSizeBytes) {
    }
}
