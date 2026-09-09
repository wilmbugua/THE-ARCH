package com.kalcpos.api;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@CrossOrigin(maxAge = 3600)
public class HealthController {

    @GetMapping({"/health", "/api/v1/health", "/api/v1/auth/health"})
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
