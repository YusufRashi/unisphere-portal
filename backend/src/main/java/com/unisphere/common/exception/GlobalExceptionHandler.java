package com.unisphere.common.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central translation of exceptions into Problem Details. Extending {@link ResponseEntityExceptionHandler}
 * gives correct statuses for all standard Spring MVC exceptions (405, 415, missing parameters...);
 * {@link #handleExceptionInternal} then adds our {@code code}, {@code timestamp} and {@code requestId}.
 *
 * <p>Unexpected exceptions are logged with their stack trace server-side and returned as a generic 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ProblemDetail body = problems.create(ex.getCode(), ex.getMessage());
        ex.getProperties().forEach(body::setProperty);
        return ResponseEntity.status(ex.getCode().status()).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.status())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problems.create(ErrorCode.UNAUTHENTICATED, "Authentication is required to access this resource."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status())
                .body(problems.create(ErrorCode.FORBIDDEN, "You do not have permission to perform this action."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(problems.create(ErrorCode.MALFORMED_REQUEST, "Parameter '%s' has an invalid value.".formatted(ex.getName())));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(ErrorCode.CONCURRENT_MODIFICATION.status())
                .body(problems.create(ErrorCode.CONCURRENT_MODIFICATION, "The record was changed by another user. Reload and try again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Services check for duplicates up front; reaching this means a race or a missed check.
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.DATA_CONFLICT.status())
                .body(problems.create(ErrorCode.DATA_CONFLICT, "The request conflicts with existing data."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(problems.create(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred. Please try again later."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())));
        }
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                errors.add(Map.of("field", ge.getObjectName(), "message", String.valueOf(ge.getDefaultMessage()))));
        return validationProblem(errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(err ->
                    errors.add(Map.of("field", String.valueOf(name), "message", String.valueOf(err.getDefaultMessage()))));
        });
        return validationProblem(errors);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail pd
                ? pd
                : ProblemDetail.forStatus(statusCode);
        ErrorCode code = ErrorCode.fromStatus(statusCode.value());
        // Replace framework-generated detail texts, which can echo internal class names.
        problem.setDetail(defaultDetail(code));
        problems.enrich(problem, code);
        return ResponseEntity.status(statusCode).headers(headers).body(problem);
    }

    private ResponseEntity<Object> validationProblem(List<Map<String, String>> errors) {
        ProblemDetail problem = problems.create(ErrorCode.VALIDATION_ERROR, "One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private static String defaultDetail(ErrorCode code) {
        return switch (code) {
            case NOT_FOUND -> "The requested resource does not exist.";
            case METHOD_NOT_ALLOWED -> "This HTTP method is not supported for this resource.";
            case UNSUPPORTED_MEDIA_TYPE -> "The request content type is not supported.";
            case NOT_ACCEPTABLE -> "The requested response format is not available.";
            case MALFORMED_REQUEST -> "The request could not be read. Check the syntax and parameters.";
            default -> code.title() + ".";
        };
    }
}
