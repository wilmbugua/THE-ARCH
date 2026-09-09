package com.kalcpos.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {
    private final JdbcTemplate jdbc;
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public PaymentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> processBillPayments(long billId, List<Map<String, Object>> payments, Map<String, Object> user, String role) {
        long orderId = ((Number) billById(billId).get("order_id")).longValue();
        Long confirmer = "super_waiter".equals(role) ? ((Number) user.get("id")).longValue() : null;
        boolean hasPendingPayment = false;

        for (Map<String, Object> payment : payments) {
            String method = normalize(String.valueOf(payment.getOrDefault("method", "cash")));
            String status = ("mpesa".equals(method) || "pdq".equals(method)) && confirmer == null ? "pending" : "auto_confirmed";
            hasPendingPayment = hasPendingPayment || "pending".equals(status);
            jdbc.update("""
                    INSERT INTO payments(order_id, bill_id, mode, status, amount, mpesa_last4, paynet_time, confirmed_by_user_id, verified_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CASE WHEN ? = 'auto_confirmed' THEN CURRENT_TIMESTAMP ELSE NULL END)
                    """,
                    orderId, billId, method, status, money(payment.get("amount")),
                    string(payment.getOrDefault("mpesaRef", payment.get("last4"))), string(payment.get("paynetTime")), confirmer, status);
        }

        if (hasPendingPayment) {
            jdbc.update("UPDATE orders SET status = 'pending' WHERE id = ?", orderId);
        } else {
            String billMethod = payments.size() == 1
                    ? normalize(String.valueOf(payments.get(0).getOrDefault("method", "cash")))
                    : null;
            jdbc.update("UPDATE bills SET status = 'paid', payment_method = ?, paid_at = CURRENT_TIMESTAMP WHERE id = ?", billMethod, billId);
            Integer pendingBills = jdbc.queryForObject("SELECT COUNT(*) FROM bills WHERE order_id = ? AND status = 'pending'", Integer.class, orderId);
            if (pendingBills != null && pendingBills == 0) {
                jdbc.update("UPDATE orders SET status = 'closed' WHERE id = ?", orderId);
            }
        }

        boolean pending = jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE bill_id = ? AND status = 'pending'", Integer.class, billId) > 0;
        return Map.of(
                pending ? "paymentPending" : "paid", true,
                "splitPayment", payments.size() > 1,
                "bill", billById(billId)
        );
    }

    @Transactional
    public Map<String, Object> decidePayment(long paymentId, boolean confirm, Map<String, Object> user) {
        Map<String, Object> payment = paymentById(paymentId);
        Object billValue = payment.get("bill_id");
        if (!(billValue instanceof Number)) {
            throw new IllegalArgumentException("Payment is not linked to a bill");
        }
        long billId = ((Number) billValue).longValue();
        long orderId = ((Number) payment.get("order_id")).longValue();

        jdbc.update("""
                UPDATE payments
                SET status = ?, confirmed_by_user_id = ?, verified_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'pending'
                """, confirm ? "confirmed" : "rejected", ((Number) user.get("id")).longValue(), paymentId);

        if (confirm) {
            Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE bill_id = ? AND status = 'pending'", Integer.class, billId);
            BigDecimal paid = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(amount), 0)
                    FROM payments
                    WHERE bill_id = ? AND status IN ('confirmed', 'auto_confirmed')
                    """, BigDecimal.class, billId);
            BigDecimal billAmount = jdbc.queryForObject("SELECT amount FROM bills WHERE id = ?", BigDecimal.class, billId);
            if ((pending == null || pending == 0) && paid != null && billAmount != null && paid.setScale(2, RoundingMode.HALF_UP).compareTo(billAmount.setScale(2, RoundingMode.HALF_UP)) == 0) {
                jdbc.update("UPDATE bills SET status = 'paid', payment_method = NULL, paid_at = CURRENT_TIMESTAMP WHERE id = ?", billId);
                Integer pendingBills = jdbc.queryForObject("SELECT COUNT(*) FROM bills WHERE order_id = ? AND status = 'pending'", Integer.class, orderId);
                if (pendingBills != null && pendingBills == 0) {
                    jdbc.update("UPDATE orders SET status = 'closed' WHERE id = ?", orderId);
                }
            }
        }

        return Map.of("ok", true, "payment", paymentById(paymentId));
    }

    private Map<String, Object> billById(long billId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.id, b.order_id, b.amount, b.status, o.waiter_id
                FROM bills b
                JOIN orders o ON o.id = b.order_id
                WHERE b.id = ?
                """, billId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Bill not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> paymentById(long paymentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, order_id, bill_id, mode, status, amount
                FROM payments
                WHERE id = ?
                """, paymentId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Payment not found");
        }
        return rows.get(0);
    }

    private String normalize(String method) {
        String clean = method == null ? "" : method.trim().toLowerCase();
        return "card".equals(clean) ? "pdq" : clean;
    }

    private BigDecimal money(Object value) {
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

