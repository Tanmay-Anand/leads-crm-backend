package com.leadrat.crm.leads.api.cognito;

import com.leadrat.crm.leads.api.core.UserRole;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Backs {@link CognitoService} when no Cognito pool is configured, so local dev without AWS
 * credentials still boots and {@code UserProvisioningService}/{@code UserServiceImpl} still
 * create local rows - just with a random id standing in for a real Cognito {@code sub}, since
 * there is no token that will ever carry it anyway. Cleaner than threading
 * {@code ObjectProvider<CognitoIdentityProviderClient>} through every call site.
 *
 * <p>Not a {@code @Service} - see {@link DefaultCognitoService}'s class doc for why the choice
 * between the two lives in one explicit factory method instead.
 */
@Slf4j
public class NoopCognitoService implements CognitoService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public UUID createUser(String email, String password, UserRole role) {
        log.warn("Cognito is not configured - creating a local-only user record for {} with no real identity. "
                + "This user will not be able to sign in.", email);
        return UUID.randomUUID();
    }

    @Override
    public void setEnabled(String email, boolean enabled) {
        log.warn("Cognito is not configured - {} for {} is local-only.", enabled ? "enable" : "disable", email);
    }

    @Override
    public void resetPassword(String email, String newPassword) {
        log.warn("Cognito is not configured - password reset for {} is a no-op.", email);
    }

    @Override
    public void deleteUser(String email) {
        log.warn("Cognito is not configured - delete for {} is local-only.", email);
    }
}
