package com.kalcpos.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@CrossOrigin(maxAge = 3600)
public class OrdersController {
    private final JdbcTemplate jdbc;

    public OrdersController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> orders(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUser(authorization);
        if (user == null) return List.of();
        String role = String.valueOf(user.get("role"));
        Object userId = user.get("id");
        if ("waiter".equals(role) || "super_waiter".equals(role)) {
            return jdbc.queryForList("""
                    SELECT o.id, o.order_no, o.waiter_id, o.status, o.total_amount, o.created_at, o.updated_at,
                           u.username AS waiter_username, COALESCE(u.real_name, u.username) AS waiter_name
                    FROM orders o
                    JOIN users u ON u.id = o.waiter_id
                    WHERE o.waiter_id = ? AND DATE(o.created_at) = CURDATE()
                    ORDER BY FIELD(o.status, 'open', 'pending', 'closed'), o.id DESC
                    """, userId);
        }
        return jdbc.queryForList("""
                SELECT o.id, o.order_no, o.waiter_id, o.status, o.total_amount, o.created_at, o.updated_at,
                       u.username AS waiter_username, COALESCE(u.real_name, u.username) AS waiter_name
                FROM orders o
                JOIN users u ON u.id = o.waiter_id
                WHERE DATE(o.created_at) = CURDATE()
                ORDER BY FIELD(o.status, 'open', 'pending', 'closed'), o.id DESC
                """);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUser(authorization);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));

        String orderNo = nextOrderNo();
        jdbc.update("""
                INSERT INTO orders (order_no, waiter_id, status, total_amount)
                VALUES (?, ?, 'open', 0)
                """, orderNo, user.get("id"));
        Map<String, Object> created = jdbc.queryForMap("SELECT * FROM orders WHERE order_no = ?", orderNo);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{orderId}/items")
    public List<Map<String, Object>> items(@PathVariable long orderId) {
        return jdbc.queryForList("""
                SELECT id, order_id, product_id, item_name, qty, unit_price, line_total,
                       COALESCE(printed_qty, 0) AS printed_qty,
                       GREATEST(qty - COALESCE(printed_qty, 0), 0) AS unprinted_qty,
                       qty AS billable_qty,
                       created_at, updated_at
                FROM order_items
                WHERE order_id = ?
                ORDER BY id
                """, orderId);
    }

    @PostMapping("/{orderId}/items")
    public ResponseEntity<Map<String, Object>> addItem(
            @PathVariable long orderId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (currentUser(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        if (!isOpen(orderId)) return badRequest("Only open carts can be changed.");

        Long productId = longOrNull(body.get("productId"));
        String itemName = String.valueOf(body.getOrDefault("itemName", body.getOrDefault("name", ""))).trim();
        int qty = Math.max(1, intValue(body.getOrDefault("qty", 1)));
        BigDecimal unitPrice = decimalValue(body.getOrDefault("unitPrice", body.getOrDefault("price_ksh", 0)));
        if (itemName.isBlank() && productId != null) {
            Map<String, Object> product = jdbc.queryForMap("SELECT name, price_ksh FROM products WHERE id = ?", productId);
            itemName = String.valueOf(product.get("name"));
            unitPrice = decimalValue(product.get("price_ksh"));
        }
        if (itemName.isBlank()) return badRequest("Item name is required.");

        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, item_name, qty, unit_price, line_total)
                VALUES (?, ?, ?, ?, ?, ?)
                """, orderId, productId, itemName, qty, unitPrice, lineTotal);
        recalcOrder(orderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true));
    }

    @PatchMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<Map<String, Object>> updateItem(
            @PathVariable long orderId,
            @PathVariable long itemId,
            @RequestBody Map<String, Object> body) {
        if (!isOpen(orderId)) return badRequest("Only open carts can be changed.");
        int qty = Math.max(1, intValue(body.getOrDefault("qty", 1)));
        int updated = jdbc.update("""
                UPDATE order_items
                SET qty = ?, line_total = unit_price * ?
                WHERE id = ? AND order_id = ? AND COALESCE(printed_qty, 0) = 0
                """, qty, qty, itemId, orderId);
        if (updated == 0) return badRequest("Sent items cannot be changed.");
        recalcOrder(orderId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<Map<String, Object>> deleteItem(@PathVariable long orderId, @PathVariable long itemId) {
        if (!isOpen(orderId)) return badRequest("Only open carts can be changed.");
        jdbc.update("DELETE FROM order_items WHERE id = ? AND order_id = ? AND COALESCE(printed_qty, 0) = 0", itemId, orderId);
        recalcOrder(orderId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{orderId}/recall")
    public ResponseEntity<Map<String, Object>> recallOrder(
            @PathVariable long orderId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (currentUser(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        int updated = jdbc.update("""
                UPDATE orders
                SET status = 'open'
                WHERE id = ? AND status IN ('pending', 'closed')
                """, orderId);
        if (updated == 0 && jdbc.queryForList("SELECT id FROM orders WHERE id = ?", orderId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Order not found."));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{orderId}/void")
    public ResponseEntity<Map<String, Object>> voidOrder(
            @PathVariable long orderId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUser(authorization);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        String role = String.valueOf(user.get("role"));
        if (!List.of("admin", "manager", "supervisor", "super_waiter").contains(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Insufficient permissions."));
        }
        jdbc.update("UPDATE orders SET status = 'closed', total_amount = 0 WHERE id = ?", orderId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private Map<String, Object> currentUser(String authorization) {
        String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.id, u.username, u.role, COALESCE(u.real_name, u.username) AS real_name
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
                """, token);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean isOpen(long orderId) {
        List<String> rows = jdbc.queryForList("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        return !rows.isEmpty() && "open".equals(rows.get(0));
    }

    private void recalcOrder(long orderId) {
        jdbc.update("""
                UPDATE orders
                SET total_amount = COALESCE((SELECT SUM(line_total) FROM order_items WHERE order_id = ?), 0)
                WHERE id = ?
                """, orderId, orderId);
    }

    private String nextOrderNo() {
        String prefix = "ORD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_no LIKE ?", Integer.class, prefix + "%");
        return prefix + String.format("%04d", (count == null ? 0 : count) + 1);
    }

    private Long longOrNull(Object value) {
        if (value == null) return null;
        try {
            String text = String.valueOf(value);
            if (text.isBlank() || "null".equalsIgnoreCase(text)) return null;
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int intValue(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 1;
        }
    }

    private BigDecimal decimalValue(Object value) {
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
