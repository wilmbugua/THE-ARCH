package com.kalcpos.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import java.util.*;

@RestController
public class RootController {

    /**
     * API info endpoint - returns API information
     */
    @GetMapping("/api")
    @CrossOrigin(maxAge = 3600)
    public Map<String, Object> apiInfo() {
        return Map.of(
            "name", "KALC POS Backend",
            "version", "0.0.1-SNAPSHOT",
            "status", "online",
            "endpoints", Map.of(
                "auth", "/api/v1/auth",
                "payments", "/api/v1/payments",
                "health", "/health"
            ),
            "availableEndpoints", List.of(
                "GET  /api                           - This endpoint (API info)",
                "GET  /health                        - Backend health check",
                "GET  /api/v1/health                 - API health check",
                "GET  /api/v1/auth/health            - Authentication service health check",
                "POST /api/v1/auth/pin-login         - Login with PIN",
                "POST /api/v1/auth/logout            - Logout user",
                "GET  /api/v1/auth/verify            - Verify current session",
                "GET  /api/v1/payments/recent        - Get recent payments",
                "GET  /api/v1/payments/stats         - Get payment statistics",
                "GET  /api/v1/payments/{paymentId}   - Get payment details",
                "POST /api/v1/payments/{id}/confirm  - Confirm pending payment",
                "POST /api/v1/payments/{id}/reject   - Reject pending payment"
            )
        );
    }

    /**
     * Root endpoint and SPA fallback - serve frontend HTML
     */
    @GetMapping(value = {"/", "/**"})
    public ResponseEntity<Resource> serveFrontend() {
        try {
            Resource resource = new ClassPathResource("static/index.html");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}

