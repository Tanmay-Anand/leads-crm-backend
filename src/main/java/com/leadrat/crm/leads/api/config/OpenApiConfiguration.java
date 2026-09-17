package com.leadrat.crm.leads.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI leadsCrmOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Leads CRM API")
                        .version("v1")
                        .description("Leads, Projects and Channel Partners."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Cognito ID token.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /**
     * Documents {@code x-tenant-id} on every operation. It is optional for a tenant-scoped user
     * (the claim covers them) and required for a platform user, which no annotation can express
     * per-operation, so it is declared once here as optional.
     */
    @Bean
    public GlobalOpenApiCustomizer tenantHeaderCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation ->
                            operation.addParametersItem(new Parameter()
                                    .in("header")
                                    .name("x-tenant-id")
                                    .required(false)
                                    .description("Tenant UUID. Required when the token carries no tenant claim."))));
        };
    }
}
