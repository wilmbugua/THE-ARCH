package com.kalcpos.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(maxAge = 3600)
public class AuthController {
    
    private final JdbcTemplate jdbc;
    private static final int TOKEN_VALIDITY_MINUTES = 480; // 8 hours
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/users-debug")
    public List<Map<String, Object>> debugUsers() {
        return jdbc.queryForList("SELECT id, username, role, active, SUBSTRING(pin_hash, 1, 20) as pin_hash_preview FROM users ORDER BY id");
    }

    /**
     * Authenticate user with PIN and create session
     * @param loginRequest Username and PIN
     * @return Token and user info on success
     */
    @PostMapping("/pin-login")
    public Map<String, Object> pinLogin(@RequestBody Map<String, Object> loginRequest) {
        try {
            String username = String.valueOf(loginRequest.getOrDefault("username", "")).trim();
            String pin = String.valueOf(loginRequest.getOrDefault("pin", "")).trim();

            log.info("PIN login attempt for username: {}", username);

            if (pin.isEmpty()) {
                log.warn("Login rejected: missing PIN");
                return errorResponse("PIN is required");
            }

            List<Map<String, Object>> userRows = username.isEmpty()
                ? jdbc.queryForList("SELECT id, username, role, pin_hash, active FROM users WHERE active = 1 ORDER BY id")
                : jdbc.queryForList(
                    "SELECT id, username, role, pin_hash, active FROM users WHERE username = ?",
                    username);

            if (userRows.isEmpty()) {
                log.warn("Login failed: no matching active user rows for username '{}'", username);
                return errorResponse("Invalid username or PIN");
            }

            Map<String, Object> user = null;
            for (Map<String, Object> candidate : userRows) {
                Object activeObj = candidate.get("active");
                boolean active = false;
                if (activeObj instanceof Number) {
                    active = ((Number) activeObj).intValue() == 1;
                } else if (activeObj instanceof Boolean) {
                    active = (Boolean) activeObj;
                } else {
                    active = String.valueOf(activeObj).equals("1") || String.valueOf(activeObj).equalsIgnoreCase("true");
                }
                if (!active) {
                    continue;
                }

                String storedHash = String.valueOf(candidate.get("pin_hash"));
                if (verifyPin(pin, storedHash, candidate.get("id"), String.valueOf(candidate.get("username")))) {
                    user = candidate;
                    break;
                }
            }

            if (user == null) {
                log.warn("Login rejected: PIN verification failed for username '{}'", username);
                return errorResponse("Invalid username or PIN");
            }

            log.info("PIN login accepted for username: {}", user.get("username"));

            Object activeObj = user.get("active");
            boolean active = false;
            if (activeObj instanceof Number) {
                active = ((Number) activeObj).intValue() == 1;
            } else if (activeObj instanceof Boolean) {
                active = (Boolean) activeObj;
            } else {
                active = String.valueOf(activeObj).equals("1") || String.valueOf(activeObj).equalsIgnoreCase("true");
            }
            if (!active) {
                return errorResponse("User account is inactive");
            }

            // Generate session token
            String token = generateToken();
            LocalDateTime expiresAt = LocalDateTime.now().plus(TOKEN_VALIDITY_MINUTES, ChronoUnit.MINUTES);

            // Create auth session
            jdbc.update(
                "INSERT INTO auth_sessions (token, user_id, expires_at) VALUES (?, ?, ?)",
                token,
                user.get("id"),
                expiresAt);

            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", Map.of(
                "id", user.get("id"),
                "username", user.get("username"),
                "role", user.get("role"),
                "name", user.get("username")
            ));
            return response;

        } catch (Exception e) {
            log.error("Authentication error for username {}: {}", loginRequest.getOrDefault("username", ""), e.getMessage());
            return errorResponse("Authentication failed");
        }
    }

    /**
     * Logout user and invalidate session
     */
    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
            
            if (!token.isEmpty()) {
                jdbc.update("DELETE FROM auth_sessions WHERE token = ?", token);
            }

            return Map.of("success", true);
        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            return errorResponse("Logout failed");
        }
    }

    /**
     * Verify current session is valid
     */
    @GetMapping("/verify")
    public Map<String, Object> verify(@RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
            List<Map<String, Object>> userRows = jdbc.queryForList(
                """
                SELECT u.id, u.username, u.role
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
                """,
                token);

            if (userRows.isEmpty()) {
                return Map.of("valid", false);
            }

            Map<String, Object> user = userRows.get(0);
            return Map.of(
                "valid", true,
                "user", Map.of(
                    "id", user.get("id"),
                    "username", user.get("username"),
                    "role", user.get("role")
                )
            );
        } catch (Exception e) {
            log.error("Session verify failed: {}", e.getMessage());
            return Map.of("valid", false);
        }
    }

    /**
     * Verify PIN against stored hash
     */
    /**
     * Compute SHA256 hash of PIN (legacy support)
     */
    private String sha256Hash(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private boolean verifyPin(String pin, String storedHash, Object userId, String username) {
        if (storedHash == null || storedHash.isBlank() || "null".equalsIgnoreCase(storedHash)) {
            return false;
        }

        if (storedHash.startsWith("$2")) {
            return passwordEncoder.matches(pin, storedHash);
        }

        try {
            String computed = sha256Hash(pin);
            if (computed.equalsIgnoreCase(storedHash)) {
                String newHash = passwordEncoder.encode(pin);
                jdbc.update("UPDATE users SET pin_hash = ? WHERE id = ?", newHash, userId);
                log.info("Upgraded legacy PIN hash to BCrypt for user: {}", username);
                return true;
            }
        } catch (Exception ex) {
            log.warn("Error verifying legacy PIN hash for user {}: {}", username, ex.getMessage());
        }
        return false;
    }

    /**
     * Generate secure random token
     */
    private String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Error response helper
     */
    private Map<String, Object> errorResponse(String message) {
        return Map.of("error", message, "success", false);
    }
}
