package com.shopapp.commonlibrary.model;

import com.shopapp.commonlibrary.model.listener.CustomAuditingEntityListener;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.ZonedDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * Base class for entities that record when and by whom they were created and last modified.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(CustomAuditingEntityListener.class)
public abstract class AbstractAuditEntity {

    @CreationTimestamp
    private ZonedDateTime createdOn;

    @CreatedBy
    private String createdBy;

    @UpdateTimestamp
    private ZonedDateTime lastModifiedOn;

    @LastModifiedBy
    private String lastModifiedBy;
}
