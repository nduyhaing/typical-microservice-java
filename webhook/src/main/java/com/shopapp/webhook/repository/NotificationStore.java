package com.shopapp.webhook.repository;

import com.shopapp.webhook.model.NotificationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Writes of the delivery log, which is also the retry queue (decisions.md 5-34): plain SQL for the idempotent
 * insert and the {@code FOR UPDATE SKIP LOCKED} claim.
 */
@Repository
@RequiredArgsConstructor
public class NotificationStore {

    private static final String CLAIM_DUE = """
        UPDATE webhook_event_notification
           SET status = 'SENDING', next_attempt_on = now() + (? * interval '1 millisecond')
         WHERE id IN (SELECT id FROM webhook_event_notification
                       WHERE status IN ('PENDING', 'RETRYING', 'SENDING') AND next_attempt_on <= now()
                       ORDER BY next_attempt_on, id
                       LIMIT ?
                       FOR UPDATE SKIP LOCKED)
        RETURNING id""";

    private final JdbcTemplate jdbc;

    /**
     * A delivery due now; nothing when this webhook already has one for {@code sourceKey} (a redelivered CDC
     * message).
     *
     * @return true when inserted
     */
    public boolean insert(long webhookId, String eventName, UUID deliveryId, String sourceKey, String payload,
                          String payloadSha256) {
        return jdbc.update("""
            INSERT INTO webhook_event_notification
                (webhook_id, event_name, delivery_id, source_key, payload, payload_sha256, status, attempts,
                 created_on, next_attempt_on)
            VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, now(), now())
            ON CONFLICT (webhook_id, source_key) DO NOTHING""",
            webhookId, eventName, deliveryId, sourceKey, payload, payloadSha256) == 1;
    }

    /**
     * Marks up to {@code limit} due deliveries SENDING with a lease of {@code leaseMs} (a worker that dies leaves
     * them due again after the lease) and returns their ids.
     */
    public List<Long> claimDue(int limit, long leaseMs) {
        return jdbc.queryForList(CLAIM_DUE, Long.class, leaseMs, limit);
    }

    /** A claimed delivery with what the worker needs to send it. */
    public record Due(long id, String eventName, UUID deliveryId, String payload, int attempts, String url,
                      String secret, boolean active) {
    }

    public Optional<Due> loadClaimed(long id) {
        return jdbc.query("""
                SELECT n.id, n.event_name, n.delivery_id, n.payload, n.attempts, w.payload_url, w.secret, w.is_active
                  FROM webhook_event_notification n JOIN webhook w ON w.id = n.webhook_id
                 WHERE n.id = ? AND n.status = 'SENDING'""",
            (rs, row) -> new Due(rs.getLong("id"), rs.getString("event_name"), rs.getObject("delivery_id", UUID.class),
                rs.getString("payload"), rs.getInt("attempts"), rs.getString("payload_url"), rs.getString("secret"),
                rs.getBoolean("is_active")), id).stream().findFirst();
    }

    /**
     * Records an attempt.
     *
     * @param nextAttemptInMs when the next attempt is due (RETRYING), null when none
     */
    public void recordAttempt(long id, NotificationStatus status, int attempts, Integer responseCode, String error,
                              Long nextAttemptInMs) {
        jdbc.update("""
            UPDATE webhook_event_notification
               SET status = ?, attempts = ?, response_code = ?, last_error = ?, last_attempt_on = now(),
                   next_attempt_on = CASE WHEN ?::bigint IS NULL THEN NULL
                                          ELSE now() + (?::bigint * interval '1 millisecond') END,
                   delivered_on = CASE WHEN ? = 'DELIVERED' THEN now() ELSE delivered_on END
             WHERE id = ?""",
            status.name(), attempts, responseCode, error, nextAttemptInMs, nextAttemptInMs, status.name(), id);
    }

    /** The webhook was deactivated: the delivery is not sent. */
    public void cancel(long id) {
        jdbc.update("UPDATE webhook_event_notification SET status = 'CANCELLED', next_attempt_on = NULL WHERE id = ?",
            id);
    }
}
