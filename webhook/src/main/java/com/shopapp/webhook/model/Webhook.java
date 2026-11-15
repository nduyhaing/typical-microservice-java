package com.shopapp.webhook.model;

import com.shopapp.commonlibrary.model.AbstractAuditEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A registered receiver URL (YAS Webhook). The secret is stored to sign deliveries and never returned in full. */
@Entity
@Table(name = "webhook")
@Getter
@Setter
@NoArgsConstructor
public class Webhook extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payload_url", nullable = false)
    private String payloadUrl;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private String secret;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @OneToMany(mappedBy = "webhook", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WebhookEvent> webhookEvents = new ArrayList<>();
}
