package com.shopapp.payment.model;

import com.shopapp.commonlibrary.model.AbstractAuditEntity;
import com.shopapp.payment.model.enumeration.PaymentMethod;
import com.shopapp.payment.model.enumeration.PaymentStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One payment attempt for an order (YAS {@code Payment}). {@code amount} is always the order total read from the
 * order service, never a client value; {@code customerId} is the order's owner (JWT {@code sub}).
 * {@code paymentProviderCheckoutId} is the PayPal order id, {@code gatewayTransactionId} the PayPal capture id (both
 * unique). {@code orderConfirmed}: the order service accepted the payment (decisions.md 5-12, 5-13);
 * {@code orderRefused}: it refused it (refund by hand, 5-61).
 */
@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    private String checkoutId;

    private String customerId;

    private BigDecimal amount;

    private BigDecimal paymentFee;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    private String gatewayTransactionId;

    private String paymentProviderCheckoutId;

    private String failureMessage;

    private boolean orderConfirmed;

    /** Order refused the completed payment (e.g. cancelled or paid otherwise): needs a manual refund (5-61). */
    private boolean orderRefused;

    /** How often telling order about the completed payment failed, and when the reconciler tries next (5-61). */
    private int orderConfirmAttempts;

    private ZonedDateTime nextOrderConfirmOn;

    /** This payment's own PayPal-Request-Id for creating its PayPal order (5-67). */
    private UUID paypalRequestId;

    /** Optimistic lock: a stale copy can never overwrite a newer state (5-60). */
    @Version
    private Long version;

    /** Our reference at PayPal ({@code custom_id}): checked again when the capture comes back. */
    public String reference() {
        return "payment-" + id;
    }
}
