package com.examprep.web.advice;

import com.examprep.common.exception.AiNotConfiguredException;
import com.examprep.common.exception.AiServiceException;
import com.examprep.common.exception.InvalidRequestException;
import com.examprep.common.exception.InvalidUploadException;
import com.examprep.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(annotations = Controller.class)
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound(ResourceNotFoundException ex) {
        return render(HttpStatus.NOT_FOUND, "We could not find that page",
                "The item does not exist, or it does not belong to your account.");
    }

    @ExceptionHandler({InvalidRequestException.class, InvalidUploadException.class,
            MaxUploadSizeExceededException.class})
    public ModelAndView badRequest(Exception ex) {
        String message = ex instanceof MaxUploadSizeExceededException
                ? "The file is larger than the configured upload limit."
                : ex.getMessage();
        return render(HttpStatus.BAD_REQUEST, "That action could not be completed", message);
    }

    @ExceptionHandler(AiNotConfiguredException.class)
    public ModelAndView aiNotConfigured(AiNotConfiguredException ex) {
        return render(HttpStatus.SERVICE_UNAVAILABLE, "AI features are not configured", ex.getMessage());
    }

    @ExceptionHandler(AiServiceException.class)
    public ModelAndView aiFailure(AiServiceException ex) {
        log.warn("AI request failed: {}", ex.getMessage());
        return render(HttpStatus.BAD_GATEWAY, "The AI request failed", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception ex) {
        log.error("Unhandled web error", ex);
        return render(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong",
                "The page could not be completed. Please try again.");
    }

    private static ModelAndView render(HttpStatus status, String heading, String message) {
        ModelAndView view = new ModelAndView("error", status);
        view.addObject("status", status.value());
        view.addObject("heading", heading);
        view.addObject("message", message);
        return view;
    }
}
