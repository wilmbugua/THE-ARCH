package com.kalcpos.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/receipts")
@CrossOrigin(maxAge = 3600)
public class ReceiptsController {
    private final JdbcTemplate jdbc;

    public ReceiptsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/orders/{orderId}/send-to-station/{stationId}")
    public ResponseEntity<Map<String, Object>> sendToStation(
            @PathVariable long orderId,
            @PathVariable long stationId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (currentUser(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }

        List<Map<String, Object>> orderRows = jdbc.queryForList("""
                SELECT o.id, o.order_no, o.status, o.total_amount, o.created_at,
                       u.username AS waiter_username, COALESCE(u.real_name, u.username) AS waiter_name
                FROM orders o
                JOIN users u ON u.id = o.waiter_id
                WHERE o.id = ?
                """, orderId);
        if (orderRows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Order not found."));
        }

        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT id, item_name, qty, unit_price, line_total,
                       COALESCE(printed_qty, 0) AS printed_qty,
                       GREATEST(qty - COALESCE(printed_qty, 0), 0) AS print_qty
                FROM order_items
                WHERE order_id = ? AND qty > COALESCE(printed_qty, 0)
                ORDER BY id
                """, orderId);
        if (items.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No new items to send."));
        }

        jdbc.update("""
                UPDATE order_items
                SET printed_qty = qty
                WHERE order_id = ? AND qty > COALESCE(printed_qty, 0)
                """, orderId);
        jdbc.update("UPDATE orders SET status = 'pending' WHERE id = ? AND status = 'open'", orderId);

        Map<String, Object> order = jdbc.queryForMap("""
                SELECT o.id, o.order_no, o.status, o.total_amount, o.created_at,
                       u.username AS waiter_username, COALESCE(u.real_name, u.username) AS waiter_name
                FROM orders o
                JOIN users u ON u.id = o.waiter_id
                WHERE o.id = ?
                """, orderId);

        String stationName = stationId == 2 ? "Bar" : "Restaurant";
        boolean printed = printStationTicket(stationName, order, items);

        return ResponseEntity.ok(Map.of(
                "order", order,
                "items", items,
                "stationId", stationId,
                "stationName", stationName,
                "stationType", stationId == 2 ? "BAR" : "KITCHEN",
                "title", stationId == 2 ? "Bar Order" : "Restaurant Order",
                "serverPrinted", printed
        ));
    }

    @GetMapping("/bills")
    public List<Map<String, Object>> bills(@RequestParam(required = false) Long userId) {
        if (userId == null) {
            return jdbc.queryForList("""
                    SELECT b.id, b.bill_no, b.order_id, b.amount, b.status, b.payment_method, b.paid_at, b.created_at,
                           o.order_no, u.username AS waiter_username, COALESCE(u.real_name, u.username) AS waiter_name
                    FROM bills b
                    JOIN orders o ON o.id = b.order_id
                    JOIN users u ON u.id = o.waiter_id
                    ORDER BY b.id DESC
                    LIMIT 200
                    """);
        }
        return jdbc.queryForList("""
                SELECT b.id, b.bill_no, b.order_id, b.amount, b.status, b.payment_method, b.paid_at, b.created_at,
                       o.order_no, u.username AS waiter_username, COALESCE(u.real_name, u.username) AS waiter_name
                FROM bills b
                JOIN orders o ON o.id = b.order_id
                JOIN users u ON u.id = o.waiter_id
                WHERE o.waiter_id = ?
                ORDER BY b.id DESC
                LIMIT 200
                """, userId);
    }

    @PostMapping("/from-order/{orderId}")
    public ResponseEntity<Map<String, Object>> fromOrder(
            @PathVariable long orderId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (currentUser(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }

        List<Map<String, Object>> orderRows = jdbc.queryForList("""
                SELECT o.id, o.order_no, o.status, o.total_amount, o.created_at,
                       u.username AS waiter_username, COALESCE(u.real_name, u.username) AS waiter_name
                FROM orders o
                JOIN users u ON u.id = o.waiter_id
                WHERE o.id = ?
                """, orderId);
        if (orderRows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Order not found."));
        }

        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT id, item_name, qty, unit_price, line_total
                FROM order_items
                WHERE order_id = ?
                ORDER BY id
                """, orderId);
        if (items.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No items found for this order."));
        }

        BigDecimal amount = items.stream()
                .map(item -> decimalValue(item.get("line_total")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String receiptNo = nextDocumentNo("RCP");
        String billNo = nextDocumentNo("BILL");

        jdbc.update("""
                INSERT INTO receipts (receipt_no, order_id, receipt_type, amount, payload_json)
                VALUES (?, ?, 'single', ?, ?)
                """, receiptNo, orderId, amount, "");
        Long receiptId = jdbc.queryForObject("SELECT id FROM receipts WHERE receipt_no = ?", Long.class, receiptNo);
        jdbc.update("""
                INSERT INTO bills (bill_no, receipt_id, order_id, amount, status)
                VALUES (?, ?, ?, ?, 'pending')
                """, billNo, receiptId, orderId, amount);
        Long billId = jdbc.queryForObject("SELECT id FROM bills WHERE bill_no = ?", Long.class, billNo);

        Map<String, Object> receipt = jdbc.queryForMap("SELECT * FROM receipts WHERE id = ?", receiptId);
        Map<String, Object> bill = jdbc.queryForMap("SELECT * FROM bills WHERE id = ?", billId);
        return ResponseEntity.ok(Map.of(
                "order", orderRows.get(0),
                "receipt", receipt,
                "bill", bill,
                "items", items
        ));
    }

    private Map<String, Object> currentUser(String authorization) {
        String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.id, u.username, u.role
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
                """, token);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean printStationTicket(String stationName, Map<String, Object> order, List<Map<String, Object>> items) {
        try {
            PrintService printer = choosePrinter(stationName);
            if (printer == null) {
                return false;
            }
            String text = stationTicketText(stationName, order, items);
            DocPrintJob job = printer.createPrintJob();
            Doc doc = new SimpleDoc(text.getBytes(StandardCharsets.UTF_8), DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
            job.print(doc, null);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private PrintService choosePrinter(String stationName) {
        PrintService[] printers = PrintServiceLookup.lookupPrintServices(null, null);
        if (printers == null || printers.length == 0) {
            return null;
        }

        String preferred = getSetting(stationName.equals("Bar") ? "printer_bar" : "printer_kitchen", "");
        if (!preferred.isBlank()) {
            for (PrintService printer : printers) {
                if (printer.getName().equalsIgnoreCase(preferred)) {
                    return printer;
                }
            }
        }

        PrintService defaultPrinter = PrintServiceLookup.lookupDefaultPrintService();
        if (defaultPrinter != null) {
            return defaultPrinter;
        }
        return printers[0];
    }

    private String stationTicketText(String stationName, Map<String, Object> order, List<Map<String, Object>> items) {
        String lines = items.stream()
                .map(item -> String.format("%s x%s  Ksh %s",
                        item.get("item_name"),
                        item.get("print_qty"),
                        item.get("line_total")))
                .collect(Collectors.joining("\n"));
        return "\n" +
                stationName.toUpperCase() + " ORDER\n" +
                "Order: " + order.get("order_no") + "\n" +
                "Waiter: " + order.get("waiter_name") + "\n" +
                "Time: " + java.time.LocalDateTime.now() + "\n" +
                "------------------------------\n" +
                lines + "\n" +
                "------------------------------\n\n\n";
    }

    private String getSetting(String key, String fallback) {
        try {
            List<String> values = jdbc.queryForList("SELECT setting_value FROM system_settings WHERE setting_key = ?", String.class, key);
            return values.isEmpty() || values.get(0) == null ? fallback : values.get(0);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String nextDocumentNo(String prefix) {
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String fullPrefix = prefix + "-" + date + "-";
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM receipts WHERE receipt_no LIKE ?",
                Integer.class,
                fullPrefix + "%");
        if ("BILL".equals(prefix)) {
            count = jdbc.queryForObject("SELECT COUNT(*) FROM bills WHERE bill_no LIKE ?", Integer.class, fullPrefix + "%");
        }
        return fullPrefix + String.format("%04d", (count == null ? 0 : count) + 1);
    }

    private BigDecimal decimalValue(Object value) {
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }
}
