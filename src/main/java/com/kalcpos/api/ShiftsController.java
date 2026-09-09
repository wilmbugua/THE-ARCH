package com.kalcpos.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/shifts")
@CrossOrigin(maxAge = 3600)
public class ShiftsController {
    private final JdbcTemplate jdbc;

    public ShiftsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> shifts() {
        ensureTable();
        return jdbc.queryForList("""
                SELECT s.id, s.user_id, COALESCE(u.real_name, u.username) AS user_name, u.username, u.role,
                       s.status, s.started_at, s.ended_at, s.reconciled_at, s.manager_notes
                FROM user_shifts s
                JOIN users u ON u.id = s.user_id
                ORDER BY FIELD(s.status, 'active', 'ended', 'reconciled'), s.id DESC
                LIMIT 200
                """);
    }

    @GetMapping("/me")
    public Map<String, Object> myShift(@RequestHeader(value = "Authorization", required = false) String authorization) {
        ensureTable();
        Map<String, Object> user = currentUser(authorization);
        if (user == null) return Map.of();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, user_id, status, started_at, ended_at, reconciled_at, manager_notes
                FROM user_shifts
                WHERE user_id = ? AND status = 'active'
                ORDER BY id DESC
                LIMIT 1
                """, user.get("id"));
        return rows.isEmpty() ? Map.of("status", "none") : rows.get(0);
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestHeader(value = "Authorization", required = false) String authorization) {
        ensureTable();
        Map<String, Object> user = currentUser(authorization);
        if (user == null) return unauthorized();
        List<Map<String, Object>> active = jdbc.queryForList(
                "SELECT id FROM user_shifts WHERE user_id = ? AND status = 'active' LIMIT 1", user.get("id"));
        if (active.isEmpty()) {
            jdbc.update("INSERT INTO user_shifts(user_id, status, started_by_user_id) VALUES (?, 'active', ?)",
                    user.get("id"), user.get("id"));
        }
        return ResponseEntity.ok(myShift(authorization));
    }

    @PostMapping("/end")
    public ResponseEntity<Map<String, Object>> end(@RequestHeader(value = "Authorization", required = false) String authorization) {
        ensureTable();
        Map<String, Object> user = currentUser(authorization);
        if (user == null) return unauthorized();
        jdbc.update("""
                UPDATE user_shifts
                SET status = 'ended', ended_at = CURRENT_TIMESTAMP, ended_by_user_id = ?
                WHERE user_id = ? AND status = 'active'
                """, user.get("id"), user.get("id"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/continue")
    public ResponseEntity<Map<String, Object>> continueShift(@PathVariable long id) {
        ensureTable();
        jdbc.update("UPDATE user_shifts SET status = 'active', ended_at = NULL WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/reconcile")
    public ResponseEntity<Map<String, Object>> reconcile(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        ensureTable();
        String notes = body == null ? "" : String.valueOf(body.getOrDefault("notes", body.getOrDefault("managerNotes", ""))).trim();
        jdbc.update("""
                UPDATE user_shifts
                SET status = 'reconciled', reconciled_at = CURRENT_TIMESTAMP, manager_notes = ?
                WHERE id = ?
                """, notes, id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void ensureTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS user_shifts (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  status ENUM('active','ended','reconciled') NOT NULL DEFAULT 'active',
                  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  ended_at TIMESTAMP NULL,
                  reconciled_at TIMESTAMP NULL,
                  started_by_user_id BIGINT NULL,
                  ended_by_user_id BIGINT NULL,
                  reconciled_by_user_id BIGINT NULL,
                  manager_notes VARCHAR(500),
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
    }

    private Map<String, Object> currentUser(String authorization) {
        String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.id, u.role
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
                """, token);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
    }
}
