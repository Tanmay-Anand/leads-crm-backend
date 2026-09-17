package com.leadrat.crm.leads.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Builds the Cognito {@link JwtDecoder} from the pool id.
 *
 * <p>Declared here rather than left to {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
 * because that property makes Boot resolve the issuer metadata during context startup: with the
 * pool id not yet provisioned, the whole application fails to start with a network error rather
 * than a message about configuration. Pointing at the JWKS endpoint instead defers the fetch to the
 * first token, so the service boots, serves its docs, and rejects tokens with a 401 until the pool
 * exists.
 *
 * <p>Issuer validation is not lost by doing this: it is reattached explicitly below, alongside the
 * default timestamp checks.
 */
@Slf4j
@Configuration
public class CognitoJwtDecoderConfig {

    private final String region;
    private final String userPoolId;

    public CognitoJwtDecoderConfig(@Value("${aws.region:ap-south-1}") String region,
                                   @Value("${aws.cognito.userPoolId:}") String userPoolId) {
        this.region = region;
        this.userPoolId = userPoolId;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        if (userPoolId == null || userPoolId.isBlank()) {
            log.warn("AWS_COGNITO_USER_POOL_ID is not set. The API will start, but every authenticated request "
                    + "will fail with 401 until it is configured.");
        }

        String issuer = "https://cognito-idp." + region + ".amazonaws.com/" + userPoolId;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();

        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
