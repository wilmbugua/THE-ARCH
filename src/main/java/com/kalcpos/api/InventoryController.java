package com.kalcpos.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(maxAge = 3600)
public class InventoryController {
    private final JdbcTemplate jdbc;

    public InventoryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/suppliers")
    public List<Map<String, Object>> suppliers() {
        ensureSuppliers();
        return jdbc.queryForList("""
                SELECT id, name, phone, contact_person, active, created_at
                FROM suppliers
                ORDER BY active DESC, name
                """);
    }

    @PostMapping("/suppliers")
    public ResponseEntity<Map<String, Object>> createSupplier(@RequestBody Map<String, Object> body) {
        ensureSuppliers();
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isBlank()) return badRequest("Supplier name is required.");
        jdbc.update("""
                INSERT INTO suppliers(name, phone, contact_person, active)
                VALUES (?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE phone = VALUES(phone), contact_person = VALUES(contact_person), active = 1
                """, name, string(body.get("phone")), string(body.get("contactPerson")));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true));
    }

    @GetMapping("/purchases")
    public List<Map<String, Object>> purchases() {
        ensurePurchases();
        return jdbc.queryForList("""
                SELECT p.id, p.purchase_no, p.supplier_id, s.name AS supplier_name, p.status,
                       p.total_amount, p.invoice_no, p.notes, p.purchased_at, p.created_at
                FROM purchases p
                LEFT JOIN suppliers s ON s.id = p.supplier_id
                ORDER BY p.id DESC
                LIMIT 200
                """);
    }

    @PostMapping("/purchases")
    public ResponseEntity<Map<String, Object>> createPurchase(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ensurePurchases();
        Map<String, Object> user = currentUser(authorization);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        String purchaseNo = "PUR-" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-" + System.currentTimeMillis();
        BigDecimal total = new BigDecimal(String.valueOf(body.getOrDefault("totalAmount", body.getOrDefault("total_amount", "0"))));
        Object supplierId = body.get("supplierId") == null ? body.get("supplier_id") : body.get("supplierId");
        jdbc.update("""
                INSERT INTO purchases(purchase_no, supplier_id, status, total_amount, invoice_no, notes, created_by_user_id)
                VALUES (?, ?, 'received', ?, ?, ?, ?)
                """, purchaseNo, nullableLong(supplierId), total, string(body.get("invoiceNo")), string(body.get("notes")), user.get("id"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "purchaseNo", purchaseNo));
    }

    private void ensureSuppliers() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS suppliers (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  name VARCHAR(160) NOT NULL UNIQUE,
                  phone VARCHAR(40),
                  contact_person VARCHAR(120),
                  active TINYINT(1) NOT NULL DEFAULT 1,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void ensurePurchases() {
        ensureSuppliers();
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS purchases (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  purchase_no VARCHAR(40) NOT NULL UNIQUE,
                  supplier_id BIGINT,
                  status ENUM('draft','received','cancelled') NOT NULL DEFAULT 'received',
                  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
                  invoice_no VARCHAR(80),
                  notes TEXT,
                  purchased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  created_by_user_id BIGINT NOT NULL
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

    private Long nullableLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return Long.parseLong(String.valueOf(value));
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
