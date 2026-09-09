package com.kalcpos.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/commissions")
@CrossOrigin(maxAge = 3600)
public class CommissionsController {
    private final JdbcTemplate jdbc;

    public CommissionsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/summary")
    public List<Map<String, Object>> summary() {
        return jdbc.queryForList("""
                SELECT u.id AS user_id, u.username, COALESCE(u.real_name, u.username) AS real_name, u.role,
                       COALESCE(SUM(CASE WHEN b.status = 'paid' THEN b.amount ELSE 0 END), 0) AS sales,
                       0.01 AS rate,
                       COALESCE(SUM(CASE WHEN b.status = 'paid' THEN b.amount ELSE 0 END), 0) * 0.01 AS commission
                FROM users u
                LEFT JOIN orders o ON o.waiter_id = u.id
                LEFT JOIN bills b ON b.order_id = o.id
                WHERE u.role IN ('waiter', 'super_waiter', 'supervisor')
                GROUP BY u.id, u.username, u.real_name, u.role
                ORDER BY sales DESC, u.username
                """);
    }

    @GetMapping("/me")
    public Map<String, Object> mine(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUser(authorization);
        if (user == null) return Map.of("sales", 0, "rate", 0, "commission", 0, "orders", List.of());
        String where = "WHERE o.waiter_id = ? AND b.status = 'paid'";
        Object[] args = new Object[] { user.get("id") };
        if (from != null && to != null && !from.isBlank() && !to.isBlank()) {
            where += " AND DATE(COALESCE(b.paid_at, b.created_at)) BETWEEN ? AND ?";
            args = new Object[] { user.get("id"), from, to };
        }
        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT COALESCE(SUM(b.amount), 0) AS sales, COUNT(DISTINCT o.id) AS order_count
                FROM orders o
                JOIN bills b ON b.order_id = o.id
                """ + where, args);
        BigDecimal sales = new BigDecimal(String.valueOf(totals.get("sales")));
        BigDecimal rate = new BigDecimal("0.01");
        Map<String, Object> result = new LinkedHashMap<>(totals);
        result.put("rate", rate);
        result.put("commission", sales.multiply(rate).setScale(2, RoundingMode.HALF_UP));
        return result;
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
}
