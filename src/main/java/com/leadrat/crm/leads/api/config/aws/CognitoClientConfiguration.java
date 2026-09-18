package com.leadrat.crm.leads.api.config.aws;

import com.leadrat.crm.leads.api.cognito.CognitoService;
import com.leadrat.crm.leads.api.cognito.DefaultCognitoService;
import com.leadrat.crm.leads.api.cognito.NoopCognitoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * The single place that decides whether a real {@link CognitoIdentityProviderClient} exists and
 * which {@link CognitoService} implementation this app runs with.
 *
 * <p>One explicit factory method, not two independently-conditional {@code @Service} beans -
 * {@code @ConditionalOnBean} between plain component-scanned beans (as opposed to
 * {@code @AutoConfiguration} classes, which Spring Boot orders deliberately) does not reliably
 * see beans registered elsewhere in the same context, so picking the implementation here, with a
 * plain {@code if}, is the version that is actually guaranteed correct.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CognitoClientConfiguration {

    private final CognitoProperties properties;

    @Bean
    public CognitoService cognitoService() {
        if (!properties.isConfigured()) {
            log.warn("AWS_COGNITO_USER_POOL_ID is not set - user provisioning runs against "
                    + "NoopCognitoService until it is.");
            return new NoopCognitoService();
        }

        CognitoIdentityProviderClient client = CognitoIdentityProviderClient.builder()
                .region(Region.of(properties.region()))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
        return new DefaultCognitoService(client, properties);
    }
}
