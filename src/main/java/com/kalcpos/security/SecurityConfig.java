package com.kalcpos.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;

/**
 * Security headers and request tracking configuration.
 * Applied in production profile.
 */
@Configuration
@Profile("prod")
public class SecurityConfig {

    /**
     * Add HTTP security headers to all responses.
     * Protects against XSS, clickjacking, MIME sniffing, etc.
     */
    @Bean
    public SecurityHeadersFilter securityHeadersFilter() {
        return new SecurityHeadersFilter();
    }

    /**
     * Add request ID tracking for correlation in logs.
     */
    @Bean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    /**
     * HTTP security headers filter.
     */
    public static class SecurityHeadersFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {

            // Prevent browsers from MIME-sniffing content
            response.setHeader("X-Content-Type-Options", "nosniff");

            // Prevent clickjacking attacks
            response.setHeader("X-Frame-Options", "DENY");

            // Enable browser XSS protection
            response.setHeader("X-XSS-Protection", "1; mode=block");

            // Enforce HTTPS
            response.setHeader("Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains; preload");

            // Prevent referrer leakage
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

            // Content Security Policy
            response.setHeader("Content-Security-Policy",
                    "default-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self'; " +
                    "img-src 'self' data:; " +
                    "font-src 'self'; " +
                    "connect-src 'self'; " +
                    "frame-ancestors 'none'");

            // Permissions Policy
            response.setHeader("Permissions-Policy",
                    "accelerometer=(), camera=(), geolocation=(), " +
                    "gyroscope=(), magnetometer=(), microphone=(), " +
                    "payment=(), usb=()");

            // Disable MIME type guessing
            response.setHeader("X-Content-Type-Options", "nosniff");

            // Cache control
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");

            filterChain.doFilter(request, response);
        }
    }

    /**
     * Request ID tracking filter for correlation in logs.
     */
    public static class RequestIdFilter extends OncePerRequestFilter {

        private static final String REQUEST_ID_HEADER = "X-Request-ID";
        private static final String REQUEST_ID_MDC_KEY = "requestId";

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {

            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }

            MDC.put(REQUEST_ID_MDC_KEY, requestId);
            response.addHeader(REQUEST_ID_HEADER, requestId);

            try {
                filterChain.doFilter(request, response);
            } finally {
                MDC.remove(REQUEST_ID_MDC_KEY);
            }
        }
    }
}
