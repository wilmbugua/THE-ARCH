package com.kalcpos.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/branding")
@CrossOrigin(maxAge = 3600)
public class BrandingController {
    private final SettingsController settingsController;

    public BrandingController(JdbcTemplate jdbc) {
        this.settingsController = new SettingsController(jdbc);
    }

    @GetMapping
    public Map<String, Object> getBranding() {
        return settingsController.getSettings();
    }
}
