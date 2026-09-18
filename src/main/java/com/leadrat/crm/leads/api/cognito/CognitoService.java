package com.leadrat.crm.leads.api.cognito;

import com.leadrat.crm.leads.api.core.UserRole;

import java.util.UUID;

/**
 * Cognito admin operations for user provisioning. {@link NoopCognitoService} backs this when no
 * pool is configured, so local dev without AWS credentials still boots and still creates local
 * rows - just without a real identity behind them.
 */
public interface CognitoService {

    boolean isAvailable();

    /**
     * Creates the Cognito user and returns their {@code sub} - this is what becomes {@code
     * User.id}. Idempotent: if the email already exists in the pool, adopts that identity's
     * {@code sub} instead of failing, so a retry after a partial failure self-heals rather than
     * requiring manual cleanup.
     */
    UUID createUser(String email, String password, UserRole role);

    void setEnabled(String email, boolean enabled);

    /** Sets a new permanent password out of band - the row action behind "reset password", kept
     *  off {@code UserDto}'s edit shape entirely (see its class doc). */
    void resetPassword(String email, String newPassword);

    /** Backstop for the residual race in {@link #createUser} - removes a just-created Cognito
     *  identity when the local transaction that was supposed to follow it never lands. */
    void deleteUser(String email);
}
