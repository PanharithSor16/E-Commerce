package com.org.ecommerce.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.org.ecommerce.config.RequestIdFilter;

import tools.jackson.databind.ObjectMapper;

/** Writes problem+json responses from filters, where @RestControllerAdvice does not apply. */
@Component
public class ProblemResponder {

    private final ObjectMapper objectMapper;

    public ProblemResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, String code, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", HttpStatus.valueOf(status).getReasonPhrase());
        body.put("status", status);
        body.put("detail", detail);
        body.put("code", code);
        body.put("traceId", MDC.get(RequestIdFilter.MDC_KEY));
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
