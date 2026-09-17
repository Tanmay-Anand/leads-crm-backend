package com.leadrat.crm.leads.api.exception;

import com.leadrat.crm.leads.api.logging.RequestContext;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Turns every exception that escapes a controller into a LeadratException body, so clients
 * see one error shape regardless of what actually failed.
 */
@Slf4j
@RestControllerAdvice
public class HttpExceptionHandler {

    @ExceptionHandler(LeadratException.class)
    public ResponseEntity<LeadratException> onLeadratException(final LeadratException e) {
        log.error("LeadratException: {} | requestId={}", e.getMessage(), RequestContext.getRequestId(), e);
        return ResponseEntity.status(e.getStatus()).body(withRequestId(e));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<LeadratException> onValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {} | requestId={}", message, RequestContext.getRequestId());
        return status(HttpStatus.PRECONDITION_FAILED, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<LeadratException> onHandlerMethodValidation(HandlerMethodValidationException e) {
        log.warn("Handler method validation failed: {} | requestId={}", e.getMessage(), RequestContext.getRequestId());
        return status(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<LeadratException> onUnreadableBody(HttpMessageNotReadableException e) {
        log.error("HttpMessageNotReadableException: {} | requestId={}", e.getMessage(), RequestContext.getRequestId());
        Throwable root = rootLeadratCause(e);
        if (root != null) {
            return status(HttpStatus.PRECONDITION_FAILED, root.getMessage());
        }
        return status(HttpStatus.PRECONDITION_FAILED, "Malformed request body.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<LeadratException> onConstraintViolation(ConstraintViolationException e) {
        return handleConstraintViolation(e.getConstraintName(), e);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<LeadratException> onDataIntegrityViolation(DataIntegrityViolationException e) {
        String constraintName = null;
        if (e.getCause() instanceof ConstraintViolationException cve) {
            constraintName = cve.getConstraintName();
        }
        return handleConstraintViolation(constraintName, e);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<LeadratException> onOptimisticLock(ObjectOptimisticLockingFailureException e) {
        log.warn("Optimistic lock failure | requestId={}", RequestContext.getRequestId(), e);
        return status(HttpStatus.CONFLICT, "This record was changed by someone else. Reload and try again.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<LeadratException> onAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {} | requestId={}", e.getMessage(), RequestContext.getRequestId());
        return status(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<LeadratException> onAuthorizationDenied(AuthorizationDeniedException e) {
        log.warn("Authorization denied: {} | requestId={}", e.getMessage(), RequestContext.getRequestId());
        return status(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<LeadratException> onTypeMismatch(MethodArgumentTypeMismatchException e) {
        return status(HttpStatus.BAD_REQUEST, "Invalid value for parameter " + e.getName() + ".");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<LeadratException> onMissingParameter(MissingServletRequestParameterException e) {
        return status(HttpStatus.BAD_REQUEST, "Missing required parameter: " + e.getParameterName() + ".");
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<LeadratException> onInvalidDataAccess(InvalidDataAccessApiUsageException e) {
        log.error("Invalid data access | requestId={}", RequestContext.getRequestId(), e);
        return status(HttpStatus.BAD_REQUEST, "Invalid query. Check the sort and filter parameters.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<LeadratException> onNoResource(NoResourceFoundException e) {
        return status(HttpStatus.NOT_FOUND, "Resource not found.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<LeadratException> onMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return status(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<LeadratException> onUnhandled(Exception e) {
        log.error("Unhandled exception | requestId={}", RequestContext.getRequestId(), e);
        return status(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<LeadratException> handleConstraintViolation(String constraintName, Exception e) {
        log.error("Constraint violation: {} | requestId={}", constraintName, RequestContext.getRequestId(), e);

        if (constraintName != null) {
            String name = constraintName.toLowerCase();

            // The guard in LeadServiceImpl catches this first; the request that loses the race
            // reaches the constraint instead. Both must report the same status and wording, or
            // clients see two different failures for one rule.
            if (name.contains("uk_leads_tenant_project_mobile")) {
                return status(HttpStatus.CONFLICT,
                        "A lead with this mobile number already exists for this project.");
            }
            if (name.contains("uk_channel_partner_tenant_email")) {
                return status(HttpStatus.CONFLICT, "A channel partner with this email already exists.");
            }
            if (name.contains("uk_project_tenant_name")) {
                return status(HttpStatus.CONFLICT, "A project with this name already exists.");
            }
            return status(HttpStatus.PRECONDITION_FAILED, "Invalid value for " + constraintName + ".");
        }

        return status(HttpStatus.PRECONDITION_FAILED, "The request violates a data constraint.");
    }

    private Throwable rootLeadratCause(Throwable e) {
        Throwable current = e.getCause();
        while (current != null) {
            if (current instanceof LeadratException) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    private ResponseEntity<LeadratException> status(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(withRequestId(new LeadratException(message, status)));
    }

    private LeadratException withRequestId(LeadratException e) {
        e.setRequestId(RequestContext.getRequestId());
        return e;
    }
}
