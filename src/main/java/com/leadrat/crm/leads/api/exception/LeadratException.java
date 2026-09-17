package com.leadrat.crm.leads.api.exception;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

/**
 * Base application exception. Serialised straight back to the client by
 * {@link HttpExceptionHandler}, so it carries the status and a client-safe message.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadratException extends RuntimeException {

    private final HttpStatus status;
    private final String message;

    @Setter
    private String requestId;

    public LeadratException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.message = message;
    }

    @Override
    @JsonIgnore
    public StackTraceElement[] getStackTrace() {
        return super.getStackTrace();
    }

    @Override
    @JsonIgnore
    public synchronized Throwable getCause() {
        return super.getCause();
    }
}
