package com.kalcpos.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@CrossOrigin(maxAge = 3600)
public class ReportsController {
    private final JdbcTemplate jdbc;

    public ReportsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/sales")
    public Map<String, Object> sales(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        DateRange range = dateRange(from, to);

        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT
                  COALESCE(SUM(p.amount), 0) AS gross_sales,
                  COUNT(DISTINCT p.order_id) AS order_count
                FROM payments p
                WHERE p.status IN ('confirmed', 'auto_confirmed')
                  AND DATE(p.created_at) BETWEEN ? AND ?
                """, range.from(), range.to());

        List<Map<String, Object>> byWaiter = jdbc.queryForList("""
                SELECT
                  COALESCE(u.real_name, u.username) AS username,
                  COUNT(DISTINCT p.order_id) AS order_count,
                  COALESCE(SUM(p.amount), 0) AS sales
                FROM payments p
                JOIN orders o ON o.id = p.order_id
                JOIN users u ON u.id = o.waiter_id
                WHERE p.status IN ('confirmed', 'auto_confirmed')
                  AND DATE(p.created_at) BETWEEN ? AND ?
                GROUP BY u.id, COALESCE(u.real_name, u.username)
                ORDER BY sales DESC
                """, range.from(), range.to());

        List<Map<String, Object>> byMethod = jdbc.queryForList("""
                SELECT
                  p.mode AS payment_method,
                  COUNT(DISTINCT COALESCE(p.bill_id, p.order_id)) AS bill_count,
                  COALESCE(SUM(p.amount), 0) AS sales
                FROM payments p
                WHERE p.status IN ('confirmed', 'auto_confirmed')
                  AND DATE(p.created_at) BETWEEN ? AND ?
                GROUP BY p.mode
                ORDER BY sales DESC
                """, range.from(), range.to());

        List<Map<String, Object>> byDay = jdbc.queryForList("""
                SELECT
                  DATE(p.created_at) AS sales_date,
                  COUNT(DISTINCT p.order_id) AS order_count,
                  COALESCE(SUM(p.amount), 0) AS sales
                FROM payments p
                WHERE p.status IN ('confirmed', 'auto_confirmed')
                  AND DATE(p.created_at) BETWEEN ? AND ?
                GROUP BY DATE(p.created_at)
                ORDER BY sales_date
                """, range.from(), range.to());

        return Map.of(
                "from", range.from().toString(),
                "to", range.to().toString(),
                "totals", totals,
                "byWaiter", byWaiter,
                "byMethod", byMethod,
                "byDay", byDay
        );
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        DateRange range = dateRange(from, to);

        return jdbc.queryForList("""
                SELECT
                  o.id,
                  o.order_no,
                  o.status,
                  o.total_amount,
                  o.created_at,
                  u.username AS waiter_username,
                  COALESCE(u.real_name, u.username) AS waiter_name,
                  COALESCE(SUM(CASE WHEN p.status IN ('confirmed', 'auto_confirmed') THEN p.amount ELSE 0 END), 0) AS paid_amount
                FROM orders o
                JOIN users u ON u.id = o.waiter_id
                LEFT JOIN payments p ON p.order_id = o.id
                WHERE DATE(o.created_at) BETWEEN ? AND ?
                GROUP BY o.id, o.order_no, o.status, o.total_amount, o.created_at, u.username, COALESCE(u.real_name, u.username)
                ORDER BY o.created_at DESC, o.id DESC
                LIMIT 500
                """, range.from(), range.to());
    }

    private DateRange dateRange(String from, String to) {
        LocalDate today = LocalDate.now();
        LocalDate start = parseDate(from, today);
        LocalDate end = parseDate(to, today);
        if (start.isAfter(end)) {
            return new DateRange(end, start);
        }
        return new DateRange(start, end);
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return LocalDate.parse(value);
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
