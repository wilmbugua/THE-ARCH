package com.kalcpos.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/settings")
@CrossOrigin(maxAge = 3600)
public class SettingsController {
    private final JdbcTemplate jdbc;

    public SettingsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> getSettings() {
        ensureSettingsTable();
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("serviceMode", getSetting("service_mode", "both"));
        settings.put("loginImageUrl", getSetting("login_image_url", "/brand/restaurant-service.svg"));
        settings.put("commissionMode", getSetting("commission_mode", "fixed"));
        settings.put("commissionFixedPercent", parseNumber(getSetting("commission_fixed_percent", "1")));
        settings.put("commissionTiers", getSetting("commission_tiers", "100000:1,200000:2,300000:3,400000:4,500000:5,600000:6,700000:7,800000:8,900000:9,1000000:10"));
        settings.put("kitchenCommissionPercent", parseNumber(getSetting("kitchen_commission_percent", "2")));
        return settings;
    }

    @PutMapping("/login-image")
    public Map<String, Object> updateLoginImage(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireManager(authorization);
        String value = String.valueOf(body.getOrDefault("loginImageUrl", "")).trim();
        putSetting("login_image_url", value);
        return getSettings();
    }

    @PutMapping("/service-mode")
    public Map<String, Object> updateServiceMode(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireManager(authorization);
        String value = String.valueOf(body.getOrDefault("serviceMode", "both")).trim();
        if (!List.of("kitchen", "bar", "both", "restaurant").contains(value)) {
            throw new IllegalArgumentException("Invalid service mode");
        }
        putSetting("service_mode", value.equals("restaurant") ? "kitchen" : value);
        return getSettings();
    }

    @PutMapping("/commissions")
    public Map<String, Object> updateCommissions(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireManager(authorization);
        if (body.containsKey("commissionMode")) putSetting("commission_mode", String.valueOf(body.get("commissionMode")).trim());
        if (body.containsKey("commissionFixedPercent")) putSetting("commission_fixed_percent", String.valueOf(body.get("commissionFixedPercent")).trim());
        if (body.containsKey("commissionTiers")) putSetting("commission_tiers", String.valueOf(body.get("commissionTiers")).trim());
        if (body.containsKey("kitchenCommissionPercent")) putSetting("kitchen_commission_percent", String.valueOf(body.get("kitchenCommissionPercent")).trim());
        return getSettings();
    }

    private void requireManager(String authorization) {
        String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.id, u.role
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
                """, token);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Unauthorized");
        }
        String role = String.valueOf(rows.get(0).get("role"));
        if (!List.of("admin", "manager").contains(role)) {
            throw new IllegalArgumentException("Insufficient permissions");
        }
    }

    private void ensureSettingsTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS system_settings (
                  setting_key VARCHAR(80) PRIMARY KEY,
                  setting_value LONGTEXT NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
    }

    private String getSetting(String key, String fallback) {
        ensureSettingsTable();
        List<String> values = jdbc.queryForList("SELECT setting_value FROM system_settings WHERE setting_key = ?", String.class, key);
        return values.isEmpty() || values.get(0) == null ? fallback : values.get(0);
    }

    private void putSetting(String key, String value) {
        ensureSettingsTable();
        jdbc.update("""
                INSERT INTO system_settings(setting_key, setting_value)
                VALUES(?, ?)
                ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)
                """, key, value);
    }

    private Object parseNumber(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return value;
        }
    }
}
