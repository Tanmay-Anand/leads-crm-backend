package com.leadrat.crm.leads.api.exception;

import org.springframework.http.HttpStatus;

public class EntityNotFoundException extends LeadratException {

    public EntityNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
