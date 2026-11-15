package com.shopapp.webhook.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The last order status webhook saw per order (decisions.md 5-35). */
@Repository
@RequiredArgsConstructor
public class OrderStatusStore {

    private final JdbcTemplate jdbc;

    /** The recorded status, null when the order was never seen (or its status was null). */
    public String find(long orderId) {
        return jdbc.query("SELECT status FROM order_status_seen WHERE order_id = ?",
            (rs, row) -> rs.getString("status"), orderId).stream().findFirst().orElse(null);
    }

    public void save(long orderId, String status) {
        jdbc.update("""
            INSERT INTO order_status_seen (order_id, status, updated_on) VALUES (?, ?, now())
            ON CONFLICT (order_id) DO UPDATE SET status = EXCLUDED.status, updated_on = EXCLUDED.updated_on""",
            orderId, status);
    }

    public void delete(long orderId) {
        jdbc.update("DELETE FROM order_status_seen WHERE order_id = ?", orderId);
    }
}
