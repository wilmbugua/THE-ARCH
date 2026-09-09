package com.kalcpos.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/companies")
@CrossOrigin(maxAge = 3600)
public class CompaniesController {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public CompaniesController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> companies() {
        ensureTable();
        return jdbc.queryForList("""
                SELECT id, name, active, created_at, archived_at
                FROM companies
                ORDER BY active DESC, name
                """);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCompany(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ensureTable();
        if (!isAdmin(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "Company name is required."));
        jdbc.update("""
                INSERT INTO companies(name, active)
                VALUES (?, 1)
                ON DUPLICATE KEY UPDATE active = 1, archived_at = NULL
                """, name);
        String pin = String.valueOf(body.getOrDefault("pin", "")).trim();
        if (!pin.isBlank()) {
            String username = cleanUsername(name + "_admin");
            jdbc.update("""
                    INSERT INTO users(username, real_name, role, pin_hash, active)
                    VALUES (?, ?, 'admin', ?, 1)
                    ON DUPLICATE KEY UPDATE active = 1
                    """, username, name + " Admin", passwordEncoder.encode(pin));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCompany(@PathVariable long id, @RequestBody Map<String, Object> body) {
        ensureTable();
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "Company name is required."));
        int updated = jdbc.update("UPDATE companies SET name = ? WHERE id = ?", name, id);
        return updated == 0 ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Company not found."))
                : ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Map<String, Object>> archiveCompany(@PathVariable long id) {
        ensureTable();
        jdbc.update("UPDATE companies SET active = 0, archived_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void ensureTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS companies (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  name VARCHAR(255) NOT NULL,
                  active TINYINT(1) NOT NULL DEFAULT 1,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  archived_at TIMESTAMP NULL,
                  UNIQUE KEY uk_companies_name (name)
                )
                """);
    }

    private boolean isAdmin(String authorization) {
        String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.role
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
                """, token);
        return !rows.isEmpty() && "admin".equals(String.valueOf(rows.get(0).get("role")));
    }

    private String cleanUsername(String value) {
        return value.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
    }
}
