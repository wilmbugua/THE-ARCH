package com.kalcpos.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Payment Notification Controller
 * Provides endpoints for real-time payment notifications and updates
 */
@RestController
@RequestMapping("/api/v1/payments")
@CrossOrigin(maxAge = 3600)
public class PaymentNotificationController {
    
    private final JdbcTemplate jdbc;
    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationController.class);

    public PaymentNotificationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Get recent payments for notification polling
     * @param limit Maximum number of recent payments to return
     * @return List of recent payments with relevant details
     */
    @GetMapping("/recent")
    public Map<String, Object> getRecentPayments(
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        try {
            // Verify user is authenticated and is admin/staff
            String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
            List<Map<String, Object>> userRows = jdbc.queryForList("""
                SELECT u.id, u.role, COALESCE(u.real_name, u.username) AS name
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
                """, token);
            
            if (userRows.isEmpty()) {
                return Map.of("payments", Collections.emptyList(), "error", "Unauthorized");
            }

            Map<String, Object> user = userRows.get(0);
            String role = String.valueOf(user.get("role"));

            // Only allow admin and staff to see payment notifications
            if (!List.of("admin", "manager", "super_waiter").contains(role)) {
                return Map.of("payments", Collections.emptyList(), "error", "Insufficient permissions");
            }

            // Fetch recent payments with related information
            List<Map<String, Object>> payments = jdbc.queryForList("""
                SELECT 
                    p.id,
                    p.order_id,
                    p.bill_id,
                    p.mode,
                    p.status,
                    p.amount,
                    p.verified_at,
                    p.created_at,
                    p.confirmed_by_user_id,
                    b.amount as bill_amount,
                    o.id as order_id_check,
                    'Customer' as customer_name
                FROM payments p
                LEFT JOIN bills b ON b.id = p.bill_id
                LEFT JOIN orders o ON o.id = p.order_id
                ORDER BY p.id DESC
                LIMIT ?
                """, Math.min(limit, 100));

            return Map.of(
                    "payments", payments,
                    "count", payments.size(),
                    "user_id", user.get("id"),
                    "user_role", role
            );

        } catch (Exception e) {
            log.error("Failed to fetch recent payments: {}", e.getMessage(), e);
            return Map.of(
                    "payments", Collections.emptyList(),
                    "error", "Failed to fetch recent payments"
            );
        }
    }

    /**
     * Get payment statistics for dashboard
     * @return Payment statistics including counts and totals
     */
    @GetMapping("/stats")
    public Map<String, Object> getPaymentStats(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        try {
            // Verify user is authenticated
            String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
            List<Map<String, Object>> userRows = jdbc.queryForList("""
                SELECT u.id, u.role
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
                """, token);
            
            if (userRows.isEmpty()) {
                return Map.of("error", "Unauthorized");
            }

            Map<String, Object> stats = new HashMap<>();
            
            // Count payments by status
            List<Map<String, Object>> statusCounts = jdbc.queryForList("""
                SELECT status, COUNT(*) as count, SUM(amount) as total
                FROM payments
                WHERE DATE(created_at) = CURDATE()
                GROUP BY status
                """);
            
            stats.put("by_status", statusCounts);

            // Count payments by method
            List<Map<String, Object>> methodCounts = jdbc.queryForList("""
                SELECT mode, COUNT(*) as count, SUM(amount) as total
                FROM payments
                WHERE DATE(created_at) = CURDATE()
                GROUP BY mode
                """);
            
            stats.put("by_method", methodCounts);

            // Get totals
            Map<String, Object> totals = jdbc.queryForMap("""
                SELECT 
                    COUNT(*) as total_count,
                    COALESCE(SUM(amount), 0) as total_amount,
                    COUNT(CASE WHEN status = 'pending' THEN 1 END) as pending_count,
                    COUNT(CASE WHEN status IN ('confirmed', 'auto_confirmed') THEN 1 END) as confirmed_count
                FROM payments
                WHERE DATE(created_at) = CURDATE()
                """);
            
            stats.put("totals", totals);

            return stats;

        } catch (Exception e) {
            log.error("Failed to fetch payment statistics: {}", e.getMessage(), e);
            return Map.of("error", "Failed to fetch payment statistics");
        }
    }

    @GetMapping("/pending")
    public List<Map<String, Object>> getPendingPayments(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
        List<Map<String, Object>> userRows = jdbc.queryForList("""
                SELECT u.id, u.role
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
                """, token);
        if (userRows.isEmpty()) {
            return Collections.emptyList();
        }
        return jdbc.queryForList("""
                SELECT p.id, p.order_id, p.bill_id, p.mode, p.status, p.amount, p.mpesa_last4, p.paynet_time,
                       p.verified_at, p.created_at, b.bill_no, o.order_no
                FROM payments p
                LEFT JOIN bills b ON b.id = p.bill_id
                LEFT JOIN orders o ON o.id = p.order_id
                WHERE p.status = 'pending'
                ORDER BY p.id DESC
                LIMIT 100
                """);
    }

    /**
     * Get a single payment's details
     * @param paymentId Payment ID
     * @return Payment details with related bill and order information
     */
    @GetMapping("/{paymentId}")
    public Map<String, Object> getPayment(
            @PathVariable long paymentId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        try {
            // Verify user is authenticated
            String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
            List<Map<String, Object>> userRows = jdbc.queryForList("""
                SELECT u.id, u.role
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
                """, token);
            
            if (userRows.isEmpty()) {
                return Map.of("error", "Unauthorized");
            }

            List<Map<String, Object>> payments = jdbc.queryForList("""
                SELECT 
                    p.*,
                    b.amount as bill_amount,
                    b.status as bill_status,
                    'Customer' as customer_name
                FROM payments p
                LEFT JOIN bills b ON b.id = p.bill_id
                LEFT JOIN orders o ON o.id = p.order_id
                WHERE p.id = ?
                """, paymentId);
            
            if (payments.isEmpty()) {
                return Map.of("error", "Payment not found");
            }

            return Map.of("payment", payments.get(0));

        } catch (Exception e) {
            log.error("Failed to fetch payment {}: {}", paymentId, e.getMessage(), e);
            return Map.of("error", "Failed to fetch payment");
        }
    }
}
