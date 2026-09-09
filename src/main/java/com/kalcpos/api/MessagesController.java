package com.kalcpos.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/messages")
@CrossOrigin(maxAge = 3600)
public class MessagesController {
    private final JdbcTemplate jdbc;

    public MessagesController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> listMessages() {
        ensureTable();
        return jdbc.queryForList("""
                SELECT m.id, m.title, m.body, m.created_by_user_id,
                       COALESCE(u.real_name, u.username) AS created_by, m.created_at, m.updated_at
                FROM manager_messages m
                LEFT JOIN users u ON u.id = m.created_by_user_id
                ORDER BY m.id DESC
                LIMIT 100
                """);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createMessage(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ensureTable();
        Map<String, Object> user = currentUser(authorization);
        if (user == null) return unauthorized();
        if (!Set.of("admin", "manager", "supervisor").contains(String.valueOf(user.get("role")))) {
            return forbidden();
        }
        String title = String.valueOf(body.getOrDefault("title", "Message")).trim();
        String text = String.valueOf(body.getOrDefault("body", body.getOrDefault("message", ""))).trim();
        if (text.isBlank()) return badRequest("Message body is required.");
        jdbc.update("""
                INSERT INTO manager_messages(title, body, created_by_user_id)
                VALUES (?, ?, ?)
                """, title.isBlank() ? "Message" : title, text, user.get("id"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMessage(
            @PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUser(authorization);
        if (user == null) return unauthorized();
        if (!Set.of("admin", "manager", "supervisor").contains(String.valueOf(user.get("role")))) {
            return forbidden();
        }
        jdbc.update("DELETE FROM manager_messages WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void ensureTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS manager_messages (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  title VARCHAR(120) NOT NULL,
                  body TEXT NOT NULL,
                  created_by_user_id BIGINT NOT NULL,
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

    private ResponseEntity<Map<String, Object>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Insufficient permissions."));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
