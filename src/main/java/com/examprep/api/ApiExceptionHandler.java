package com.examprep.api;

import com.examprep.common.exception.AiNotConfiguredException;
import com.examprep.common.exception.AiServiceException;
import com.examprep.common.exception.EmailAlreadyRegisteredException;
import com.examprep.common.exception.InvalidRequestException;
import com.examprep.common.exception.InvalidUploadException;
import com.examprep.common.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler({InvalidRequestException.class, InvalidUploadException.class,
            MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ProblemDetail badRequest(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().findFirst()
                        .map(error -> error.getDefaultMessage()).orElse("Check the values you supplied.")
                : ex.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large",
                "The file is larger than the configured upload limit.");
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail conflict(EmailAlreadyRegisteredException ex) {
        return problem(HttpStatus.CONFLICT, "Account exists", ex.getMessage());
    }

    @ExceptionHandler(AiNotConfiguredException.class)
    public ProblemDetail notConfigured(AiNotConfiguredException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AI not configured", ex.getMessage());
    }

    @ExceptionHandler(AiServiceException.class)
    public ProblemDetail aiFailure(AiServiceException ex) {
        log.warn("AI request failed: {}", ex.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "AI request failed", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail denied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Not allowed", "You do not have access to this resource.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception ex) {
        log.error("Unhandled API error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "Something went wrong. Please try again.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                detail == null ? title : detail);
        problem.setTitle(title);
        return problem;
    }
}
