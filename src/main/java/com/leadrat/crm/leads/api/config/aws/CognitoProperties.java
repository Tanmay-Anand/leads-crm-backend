package com.leadrat.crm.leads.api.config.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Reuses the same {@code aws.region}/{@code aws.cognito.*} keys {@code CognitoJwtDecoderConfig}
 *  already binds via {@code @Value} - one source of truth for which pool this app talks to,
 *  whether validating a token or provisioning a user in it.
 *
 *  <p>Registered via {@code @EnableConfigurationProperties} on {@link CognitoClientConfiguration}
 *  rather than {@code @Component}: constructor binding (required for a record) is not supported
 *  on beans created by regular component scanning. */
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
