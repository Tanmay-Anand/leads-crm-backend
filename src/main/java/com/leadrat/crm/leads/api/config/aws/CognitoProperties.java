package com.leadrat.crm.leads.api.config.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Reuses the same {@code aws.region}/{@code aws.cognito.*} keys {@code CognitoJwtDecoderConfig}
 *  already binds via {@code @Value} - one source of truth for which pool this app talks to,
 *  whether validating a token or provisioning a user in it. */
@Component
@ConfigurationProperties(prefix = "aws")
public record CognitoProperties(String region, Cognito cognito) {

    public record Cognito(String userPoolId, String clientId) {
    }

    public String userPoolId() {
        return cognito == null ? null : cognito.userPoolId();
    }

    public boolean isConfigured() {
        String poolId = userPoolId();
        return poolId != null && !poolId.isBlank();
    }
}
