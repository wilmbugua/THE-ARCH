package com.kalcpos.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SplitBillPaymentFilter implements Filter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final com.kalcpos.service.PaymentService paymentService;
    private static final Logger log = LoggerFactory.getLogger(SplitBillPaymentFilter.class);

    public SplitBillPaymentFilter(JdbcTemplate jdbc, ObjectMapper mapper, com.kalcpos.service.PaymentService paymentService) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.paymentService = paymentService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();

        if ("POST".equalsIgnoreCase(req.getMethod()) && path.matches(".*/api/v1/payments/\\d+/(confirm|reject)$")) {
            decidePayment(req, res, path);
            return;
        }

        if (!"POST".equalsIgnoreCase(req.getMethod()) || !path.matches(".*/api/v1/receipts/bills/\\d+/pay$")) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(req);
        Map<String, Object> body = mapper.readValue(wrapped.getInputStream(), Map.class);
        Object paymentsValue = body.get("payments");
        List<?> payments = paymentsValue instanceof List<?> list ? list : List.of(body);

        try {
            long billId = Long.parseLong(path.replaceAll("^.*/bills/(\\d+)/pay$", "$1"));
            Map<String, Object> user = requireUser(req.getHeader("Authorization"));
            Map<String, Object> bill = billById(billId);

            if (!"pending".equals(String.valueOf(bill.get("status")))) {
                throw new IllegalArgumentException("Bill is already paid");
            }

            String role = String.valueOf(user.get("role"));
            if (("waiter".equals(role) || "super_waiter".equals(role))
                    && ((Number) user.get("id")).longValue() != ((Number) bill.get("waiter_id")).longValue()) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Not allowed");
                return;
            }

            BigDecimal billAmount = money(bill.get("amount"));
            BigDecimal total = BigDecimal.ZERO;
            for (Object value : payments) {
                Map<String, Object> payment = (Map<String, Object>) value;
                String method = normalize(String.valueOf(payment.getOrDefault("method", "cash")));
                if (!List.of("cash", "mpesa", "pdq").contains(method)) {
                    throw new IllegalArgumentException("Invalid payment method");
                }
                BigDecimal amount = money(payment.get("amount"));
                if (amount.signum() <= 0) {
                    throw new IllegalArgumentException("Every payment amount is required");
                }
                if ("mpesa".equals(method)) {
                    String ref = string(payment.getOrDefault("mpesaRef", payment.get("last4")));
                    String time = string(payment.get("paynetTime"));
                    if (ref.isBlank() || time.isBlank()) {
                        throw new IllegalArgumentException("M-Pesa reference and time are required");
                    }
                }
                total = total.add(amount);
            }
            if (total.compareTo(billAmount) != 0) {
                throw new IllegalArgumentException("Payment total must equal bill amount");
            }

            // Delegate transactional work to PaymentService
            Map<String, Object> result = paymentService.processBillPayments(billId, (List<Map<String, Object>>) (List<?>) payments, user, role);
            writeJson(res, result);
            return;
        } catch (IllegalArgumentException e) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error processing payment request: {}", e.getMessage(), e);
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    private void decidePayment(HttpServletRequest req, HttpServletResponse res, String path) throws IOException {
        try {
            long paymentId = Long.parseLong(path.replaceAll("^.*/payments/(\\d+)/(confirm|reject)$", "$1"));
            boolean confirm = path.endsWith("/confirm");
            Map<String, Object> user = requireUser(req.getHeader("Authorization"));
            Map<String, Object> payment = paymentById(paymentId);
            Object billValue = payment.get("bill_id");
            if (!(billValue instanceof Number billNumber)) {
                returnChainError(res, HttpServletResponse.SC_BAD_REQUEST, "Payment is not linked to a bill");
                return;
            }
            String role = String.valueOf(user.get("role"));
            String mode = normalize(String.valueOf(payment.get("mode")));
            if ("super_waiter".equals(role) && !List.of("mpesa", "pdq").contains(mode)) {
                returnChainError(res, HttpServletResponse.SC_FORBIDDEN, "Super waiter can only confirm or reject M-Pesa and PDQ payments");
                return;
            }

            // Delegate confirm/reject processing to PaymentService
            Map<String, Object> result = paymentService.decidePayment(paymentId, confirm, user);
            writeJson(res, result);
        } catch (IllegalArgumentException e) {
            returnChainError(res, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            returnChainError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private Map<String, Object> requireUser(String authorization) {
        String token = authorization == null ? "" : authorization.replaceFirst("(?i)^Bearer\\s+", "");
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT u.id, u.role
            FROM auth_sessions s
            JOIN users u ON u.id = s.user_id
            WHERE s.token = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
            """, token);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return rows.get(0);
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

    private void returnChainError(HttpServletResponse res, int status, String message) throws IOException {
        if (status == HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            log.error("Internal error: {}", message);
            res.sendError(status, "Internal server error");
        } else {
            res.sendError(status, message);
        }
    }

    private void writeJson(HttpServletResponse res, Object value) throws IOException {
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(res.getWriter(), value);
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
