package com.kalcpos.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(maxAge = 3600)
public class UsersController {
    private static final Set<String> VALID_ROLES = Set.of("admin", "manager", "supervisor", "super_waiter", "waiter");

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UsersController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> listUsers() {
        return jdbc.queryForList("""
                SELECT id, username, real_name, role, active, created_at, updated_at
                FROM users
                ORDER BY active DESC, role, COALESCE(real_name, username), id
                """);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ResponseEntity<Map<String, Object>> auth = requireManager(authorization);
        if (auth != null) {
            return auth;
        }

        String username = cleanUsername(String.valueOf(body.getOrDefault("username", "")));
        String realName = String.valueOf(body.getOrDefault("realName", body.getOrDefault("real_name", ""))).trim();
        String role = String.valueOf(body.getOrDefault("role", "waiter")).trim();
        String pin = String.valueOf(body.getOrDefault("pin", "")).trim();

        if (username.isBlank()) {
            return badRequest("Username is required.");
        }
        if (realName.isBlank()) {
            return badRequest("Employee name is required.");
        }
        if (!VALID_ROLES.contains(role)) {
            return badRequest("Invalid role.");
        }
        if (!pin.matches("\\d{8}")) {
            return badRequest("PIN must be 8 digits.");
        }
        if (!jdbc.queryForList("SELECT id FROM users WHERE username = ?", username).isEmpty()) {
            return badRequest("Username already exists. Try saving again.");
        }

        jdbc.update("""
                INSERT INTO users (username, real_name, role, pin_hash, active)
                VALUES (?, ?, ?, ?, 1)
                """, username, realName, role, passwordEncoder.encode(pin));

        Map<String, Object> created = jdbc.queryForMap("""
                SELECT id, username, real_name, role, active, created_at, updated_at
                FROM users
                WHERE username = ?
                """, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ResponseEntity<Map<String, Object>> auth = requireManager(authorization);
        if (auth != null) {
            return auth;
        }

        String realName = String.valueOf(body.getOrDefault("realName", body.getOrDefault("real_name", ""))).trim();
        String role = String.valueOf(body.getOrDefault("role", "")).trim();
        if (realName.isBlank()) {
            return badRequest("Employee name is required.");
        }
        if (!VALID_ROLES.contains(role)) {
            return badRequest("Invalid role.");
        }

        int updated = jdbc.update("UPDATE users SET real_name = ?, role = ? WHERE id = ?", realName, role, id);
        if (updated == 0) {
            return notFound("Employee not found.");
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/role")
    public ResponseEntity<Map<String, Object>> updateRole(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ResponseEntity<Map<String, Object>> auth = requireManager(authorization);
        if (auth != null) {
            return auth;
        }

        String role = String.valueOf(body.getOrDefault("role", "")).trim();
        if (!VALID_ROLES.contains(role)) {
            return badRequest("Invalid role.");
        }
        int updated = jdbc.update("UPDATE users SET role = ? WHERE id = ?", role, id);
        if (updated == 0) {
            return notFound("Employee not found.");
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/active")
    public ResponseEntity<Map<String, Object>> updateActive(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ResponseEntity<Map<String, Object>> auth = requireManager(authorization);
        if (auth != null) {
            return auth;
        }

        boolean active = Boolean.parseBoolean(String.valueOf(body.getOrDefault("active", true)));
        int updated = jdbc.update("UPDATE users SET active = ? WHERE id = ?", active ? 1 : 0, id);
        if (updated == 0) {
            return notFound("Employee not found.");
        }
        if (!active) {
            jdbc.update("DELETE FROM auth_sessions WHERE user_id = ?", id);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/reset-pin")
    public ResponseEntity<Map<String, Object>> resetPin(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ResponseEntity<Map<String, Object>> auth = requireManager(authorization);
        if (auth != null) {
            return auth;
        }

        String pin = String.valueOf(body.getOrDefault("pin", "")).trim();
        if (!pin.matches("\\d{8}")) {
            return badRequest("PIN must be 8 digits.");
        }
        int updated = jdbc.update("UPDATE users SET pin_hash = ? WHERE id = ?", passwordEncoder.encode(pin), id);
        if (updated == 0) {
            return notFound("Employee not found.");
        }
        jdbc.update("DELETE FROM auth_sessions WHERE user_id = ?", id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ResponseEntity<Map<String, Object>> auth = requireManager(authorization);
        if (auth != null) {
            return auth;
        }

        jdbc.update("DELETE FROM auth_sessions WHERE user_id = ?", id);
        int updated = jdbc.update("UPDATE users SET active = 0 WHERE id = ?", id);
        if (updated == 0) {
            return notFound("Employee not found.");
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    private ResponseEntity<Map<String, Object>> requireManager(String authorization) {
        String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.id, u.role
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
                """, token);
        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        String role = String.valueOf(rows.get(0).get("role"));
        if (!Set.of("admin", "manager").contains(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Insufficient permissions."));
        }
        return null;
    }

    private String cleanUsername(String value) {
        return value.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    private ResponseEntity<Map<String, Object>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", message));
    }
}
