package com.shopapp.payment.model;

import com.shopapp.commonlibrary.model.AbstractAuditEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

/**
 * A payment provider (YAS {@code PaymentProvider}): id = the payment method it handles (COD, PAYPAL), whether the
 * admin enabled it, and display settings. {@code additionalSettings} is display configuration only — never
 * credentials (YAS kept PayPal's client secret there; decisions.md 5-16).
 */
@DynamicUpdate
@Entity
@Table(name = "payment_provider")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentProvider extends AbstractAuditEntity implements Persistable<String> {

    @Id
    private String id;

    private boolean enabled;

    private String name;

    private String configureUrl;

    private String landingViewComponentName;

    private String additionalSettings;

    private Long mediaId;

    @Version
    private int version;

    /** Set on a provider being created: its id is assigned, so Spring Data persists instead of merging (as YAS). */
    @Transient
    private boolean isNew;

    @Override
    public boolean isNew() {
        return isNew;
    }
}
