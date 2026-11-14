package com.shopapp.commonlibrary.model.listener;

import com.shopapp.commonlibrary.model.AbstractAuditEntity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Auditing listener that keeps {@code createdBy}/{@code lastModifiedBy} values set explicitly at creation
 * (for example by data imports) instead of overwriting them with the current user.
 * Updates always record the current user.
 */
@Configurable
public class CustomAuditingEntityListener extends AuditingEntityListener {

    public CustomAuditingEntityListener(ObjectFactory<AuditingHandler> handler) {
        super.setAuditingHandler(handler);
    }

    @Override
    @PrePersist
    public void touchForCreate(Object target) {
        AbstractAuditEntity entity = (AbstractAuditEntity) target;
        if (entity.getCreatedBy() == null) {
            super.touchForCreate(target);
        } else if (entity.getLastModifiedBy() == null) {
            entity.setLastModifiedBy(entity.getCreatedBy());
        }
    }

    // YAS skipped auditing whenever lastModifiedBy was set, which after the first persist is always:
    // updates then kept the creator as modifier. Always delegating records who actually made the change.
    @Override
    @PreUpdate
    public void touchForUpdate(Object target) {
        super.touchForUpdate(target);
    }
}
