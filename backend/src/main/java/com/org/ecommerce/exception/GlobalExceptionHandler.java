package com.org.ecommerce.exception;

import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.org.ecommerce.config.RequestIdFilter;

/**
 * Converts every exception into an RFC 9457 problem response. Internal details
 * (stack traces, SQL errors, class names) are logged, never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail pd = problem(ex.getStatus(), ex.getMessage());
        pd.setProperty("code", ex.getCode());
        return ResponseEntity.status(ex.getStatus()).body(pd);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setProperty("code", "VALIDATION_FAILED");
        pd.setProperty("errors", ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", lastPathNode(v.getPropertyPath().toString()), "message", v.getMessage()))
                .toList());
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'");
        pd.setProperty("code", "INVALID_PARAMETER");
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "The resource was modified concurrently. Reload and try again.");
        pd.setProperty("code", "CONCURRENT_MODIFICATION");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "The request conflicts with existing data.");
        pd.setProperty("code", "DATA_CONFLICT");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ProblemDetail> handleAccessDenied(RuntimeException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
        pd.setProperty("code", "FORBIDDEN");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        ProblemDetail pd = problem(HttpStatus.UNAUTHORIZED, "Authentication required.");
        pd.setProperty("code", "UNAUTHORIZED");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        pd.setProperty("code", "INTERNAL_ERROR");
        return ResponseEntity.internalServerError().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setProperty("code", "VALIDATION_FAILED");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        List<Map<String, String>> global = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> Map.of("field", e.getObjectName(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        pd.setProperty("errors", errors.isEmpty() ? global : errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setProperty("code", "VALIDATION_FAILED");
        pd.setProperty("errors", ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> Map.of("field", String.valueOf(r.getMethodParameter().getParameterName()),
                                "message", String.valueOf(e.getDefaultMessage()))))
                .toList());
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.CONTENT_TOO_LARGE, "Uploaded file is too large (max 5 MB).");
        pd.setProperty("code", "FILE_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // Log the parser's reason (not the body) so malformed-request issues are diagnosable.
        log.debug("Unreadable request body: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Malformed request body.");
        pd.setProperty("code", "MALFORMED_REQUEST");
        return ResponseEntity.badRequest().body(pd);
    }

    /** Adds the trace id to problems produced by Spring MVC's built-in handlers. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail pd) {
            pd.setProperty("traceId", MDC.get(RequestIdFilter.MDC_KEY));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    static ProblemDetail problem(HttpStatusCode status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("traceId", MDC.get(RequestIdFilter.MDC_KEY));
        return pd;
    }

    private static String lastPathNode(String path) {
        int i = path.lastIndexOf('.');
        return i >= 0 ? path.substring(i + 1) : path;
    }
}
