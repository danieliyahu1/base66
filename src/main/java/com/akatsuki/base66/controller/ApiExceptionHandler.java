package com.akatsuki.base66.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public ProblemDetail handleWebClientResponseException(WebClientResponseException ex) {
        log.warn(
            "OpenCode endpoint returned error status={} uri={} body={}",
            ex.getStatusCode().value(),
            ex.getRequest() == null ? "unknown" : ex.getRequest().getURI(),
            ex.getResponseBodyAsString(),
            ex
        );

        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        detail.setTitle("OpenCode request failed");
        detail.setDetail("OpenCode returned status " + ex.getStatusCode().value()
            + ". Check OpenCode logs and configuration.");
        return detail;
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ProblemDetail handleWebClientRequestException(WebClientRequestException ex) {
        String target = ex.getUri() == null ? "configured OpenCode endpoint" : ex.getUri().toString();
        log.warn("OpenCode endpoint unreachable: {}", target, ex);

        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        detail.setTitle("OpenCode unavailable");
        detail.setDetail("Unable to reach OpenCode at " + target
            + ". Make sure OpenCode is running and OPENCODE_BASE_URL is correct.");
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Invalid request");
        detail.setDetail(ex.getMessage() == null ? "Request validation failed" : ex.getMessage());
        return detail;
    }
}
