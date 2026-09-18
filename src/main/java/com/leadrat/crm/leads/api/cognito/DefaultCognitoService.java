package com.leadrat.crm.leads.api.cognito;

import com.leadrat.crm.leads.api.config.aws.CognitoProperties;
import com.leadrat.crm.leads.api.core.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.util.List;
import java.util.UUID;

/**
 * Not a {@code @Service} - {@link com.leadrat.crm.leads.api.config.aws.CognitoClientConfiguration}
 * constructs whichever {@link CognitoService} implementation applies in one explicit factory
 * method, rather than relying on {@code @ConditionalOnBean} ordering between two independently
 * component-scanned beans (a documented Spring Boot pitfall outside auto-configuration classes).
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultCognitoService implements CognitoService {

    private final CognitoIdentityProviderClient client;
    private final CognitoProperties properties;

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public UUID createUser(String email, String password, UserRole role) {
        UUID sub;
        try {
            UserType created = client.adminCreateUser(builder -> builder
                            .userPoolId(properties.userPoolId())
                            .username(email)
                            .userAttributes(
                                    AttributeType.builder().name("email").value(email).build(),
                                    AttributeType.builder().name("email_verified").value("true").build())
                            .messageAction("SUPPRESS"))
                    .user();
            sub = extractSub(created.username(), created.attributes());
        } catch (UsernameExistsException e) {
            // Idempotency, not failure: a prior create may have succeeded in Cognito but the
            // local transaction that was meant to follow it never landed. Adopt the existing
            // identity's sub rather than erroring - the caller's own findAnyByTenantAndEmail
            // check is what decides whether to actually use it.
            log.info("Cognito user {} already exists - adopting the existing identity", email);
            AdminGetUserResponse existing = client.adminGetUser(
                    builder -> builder.userPoolId(properties.userPoolId()).username(email));
            sub = extractSub(existing.username(), existing.userAttributes());
        }

        if (password != null && !password.isBlank()) {
            client.adminSetUserPassword(builder -> builder
                    .userPoolId(properties.userPoolId())
                    .username(email)
                    .password(password)
                    .permanent(true));
        }

        client.adminAddUserToGroup(builder -> builder
                .userPoolId(properties.userPoolId())
                .username(email)
                .groupName(role.name()));

        return sub;
    }

    @Override
    public void setEnabled(String email, boolean enabled) {
        if (enabled) {
            client.adminEnableUser(builder -> builder.userPoolId(properties.userPoolId()).username(email));
        } else {
            client.adminDisableUser(builder -> builder.userPoolId(properties.userPoolId()).username(email));
        }
    }

    @Override
    public void resetPassword(String email, String newPassword) {
        client.adminSetUserPassword(builder -> builder
                .userPoolId(properties.userPoolId())
                .username(email)
                .password(newPassword)
                .permanent(true));
    }

    @Override
    public void deleteUser(String email) {
        client.adminDeleteUser(builder -> builder.userPoolId(properties.userPoolId()).username(email));
    }

    /**
     * The one attribute that must never come from anywhere else. With {@code
     * UsernameAttributes=email} on this pool, {@code UserType.username()} can itself return the
     * email (Cognito's internal alias resolution), which would make {@code User.id} something
     * no future token's {@code sub} claim ever matches - a silent identity split that only shows
     * up the next time that person signs in. The {@code sub} attribute, by contrast, is the one
     * value Cognito guarantees is stable for the life of the identity and is exactly what ends
     * up in every JWT this app validates.
     */
    private UUID extractSub(String username, List<AttributeType> attributes) {
        return attributes.stream()
                .filter(attribute -> "sub".equals(attribute.name()))
                .map(AttributeType::value)
                .map(UUID::fromString)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Cognito user " + username + " has no sub attribute"));
    }
}
