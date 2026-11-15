package com.shopapp.webhook.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.webhook.delivery.SsrfGuard;
import com.shopapp.webhook.model.Event;
import com.shopapp.webhook.model.Webhook;
import com.shopapp.webhook.model.WebhookEvent;
import com.shopapp.webhook.model.WebhookEventNotification;
import com.shopapp.webhook.repository.EventRepository;
import com.shopapp.webhook.repository.WebhookEventNotificationRepository;
import com.shopapp.webhook.repository.WebhookRepository;
import com.shopapp.webhook.utils.Constants;
import com.shopapp.webhook.utils.Constants.ErrorCode;
import com.shopapp.webhook.viewmodel.EventVm;
import com.shopapp.webhook.viewmodel.TestDeliveryVm;
import com.shopapp.webhook.viewmodel.WebhookDetailVm;
import com.shopapp.webhook.viewmodel.WebhookListGetVm;
import com.shopapp.webhook.viewmodel.WebhookNotificationListVm;
import com.shopapp.webhook.viewmodel.WebhookNotificationVm;
import com.shopapp.webhook.viewmodel.WebhookPostVm;
import com.shopapp.webhook.viewmodel.WebhookVm;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Webhook administration (YAS WebhookService) with the SSRF guard, secret rules and a cap (decisions.md 5-31). */
@Service
@RequiredArgsConstructor
public class WebhookService {

    private static final String MASK = "********";

    private final WebhookRepository webhookRepository;
    private final EventRepository eventRepository;
    private final WebhookEventNotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final SsrfGuard ssrfGuard;

    @Transactional(readOnly = true)
    public WebhookListGetVm getPageableWebhooks(int pageNo, int pageSize) {
        Page<Webhook> page = webhookRepository.findAll(
            PageRequest.of(pageNo, pageSize, Sort.by(Sort.Direction.DESC, "id")));
        return new WebhookListGetVm(page.getContent().stream().map(WebhookVm::fromModel).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /** All webhooks (at most {@link Constants#MAX_WEBHOOKS}), newest first. */
    @Transactional(readOnly = true)
    public List<WebhookVm> findAllWebhooks() {
        return webhookRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
            .map(WebhookVm::fromModel).toList();
    }

    @Transactional(readOnly = true)
    public WebhookDetailVm findById(Long id) {
        return toDetail(getWebhook(id));
    }

    @Transactional
    public WebhookDetailVm create(WebhookPostVm post) {
        if (webhookRepository.count() >= Constants.MAX_WEBHOOKS) {
            throw new BadRequestException(ErrorCode.WEBHOOK_LIMIT, Constants.MAX_WEBHOOKS);
        }
        String secret = post.secret();
        if (secret == null || secret.isEmpty()) {
            throw new BadRequestException(ErrorCode.WEBHOOK_SECRET_REQUIRED, Constants.MIN_SECRET_LENGTH);
        }
        Webhook webhook = new Webhook();
        apply(webhook, post);
        if (webhook.getIsActive() == null) {
            webhook.setIsActive(true);
        }
        return toDetail(webhookRepository.save(webhook));
    }

    @Transactional
    public void update(WebhookPostVm post, Long id) {
        apply(getWebhook(id), post);
    }

    @Transactional
    public void delete(Long id) {
        if (!webhookRepository.existsById(id)) {
            throw new NotFoundException(ErrorCode.WEBHOOK_NOT_FOUND, id);
        }
        // subscriptions and the delivery log go with it (on delete cascade)
        webhookRepository.deleteById(id);
    }

    /** Queues a PING delivery to an active webhook ("send test event"). */
    @Transactional
    public TestDeliveryVm sendTest(Long id) {
        Webhook webhook = getWebhook(id);
        if (!Boolean.TRUE.equals(webhook.getIsActive())) {
            throw new BadRequestException(ErrorCode.WEBHOOK_INACTIVE, id);
        }
        return new TestDeliveryVm(notificationService.enqueueTest(id));
    }

    @Transactional(readOnly = true)
    public WebhookNotificationListVm getNotifications(Long id, int pageNo, int pageSize) {
        if (!webhookRepository.existsById(id)) {
            throw new NotFoundException(ErrorCode.WEBHOOK_NOT_FOUND, id);
        }
        Page<WebhookEventNotification> page = notificationRepository.findByWebhookId(id,
            PageRequest.of(pageNo, pageSize, Sort.by(Sort.Direction.DESC, "id")));
        return new WebhookNotificationListVm(page.getContent().stream().map(WebhookNotificationVm::fromModel).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    private void apply(Webhook webhook, WebhookPostVm post) {
        String url = post.payloadUrl().trim();
        ssrfGuard.checkForRegistration(url);
        webhook.setPayloadUrl(url);
        webhook.setContentType(contentType(post.contentType()));
        if (post.secret() != null && !post.secret().isEmpty()) {
            if (post.secret().length() < Constants.MIN_SECRET_LENGTH || post.secret().isBlank()) {
                throw new BadRequestException(ErrorCode.WEBHOOK_SECRET_REQUIRED, Constants.MIN_SECRET_LENGTH);
            }
            webhook.setSecret(post.secret());
        }
        if (post.isActive() != null) {
            webhook.setIsActive(post.isActive());
        }
        setEvents(webhook, post.events());
    }

    private static String contentType(String requested) {
        if (requested == null || requested.isBlank()) {
            return Constants.CONTENT_TYPE_JSON;
        }
        if (!Constants.CONTENT_TYPE_JSON.equals(requested.trim().toLowerCase(Locale.ROOT))) {
            throw new BadRequestException(ErrorCode.WEBHOOK_CONTENT_TYPE, requested);
        }
        return Constants.CONTENT_TYPE_JSON;
    }

    /** Replaces the subscriptions with the given events (unknown ids: 400). */
    private void setEvents(Webhook webhook, Collection<EventVm> requested) {
        Set<Long> ids = new LinkedHashSet<>();
        if (requested != null) {
            requested.stream().filter(Objects::nonNull).forEach(event -> {
                if (event.id() == null) {
                    throw new BadRequestException(ErrorCode.EVENT_NOT_FOUND, "null");
                }
                ids.add(event.id());
            });
        }
        List<Event> events = eventRepository.findAllById(ids);
        if (events.size() != ids.size()) {
            Set<Long> found = new LinkedHashSet<>(events.stream().map(Event::getId).toList());
            Long missing = ids.stream().filter(id -> !found.contains(id)).findFirst().orElse(null);
            throw new BadRequestException(ErrorCode.EVENT_NOT_FOUND, missing);
        }
        webhook.getWebhookEvents().removeIf(existing -> !ids.contains(existing.getEvent().getId()));
        Set<Long> kept = new LinkedHashSet<>(webhook.getWebhookEvents().stream()
            .map(existing -> existing.getEvent().getId()).toList());
        events.stream().filter(event -> !kept.contains(event.getId()))
            .forEach(event -> webhook.getWebhookEvents().add(new WebhookEvent(webhook, event)));
    }

    private Webhook getWebhook(Long id) {
        return webhookRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(ErrorCode.WEBHOOK_NOT_FOUND, id));
    }

    private static WebhookDetailVm toDetail(Webhook webhook) {
        List<EventVm> events = webhook.getWebhookEvents().stream()
            .map(WebhookEvent::getEvent)
            .sorted(Comparator.comparing(Event::getId))
            .map(EventVm::fromModel)
            .toList();
        return new WebhookDetailVm(webhook.getId(), webhook.getPayloadUrl(), webhook.getContentType(),
            webhook.getIsActive(), mask(webhook.getSecret()), events);
    }

    /** Only the last four characters of a secret of 16 or more (shorter: nothing). */
    static String mask(String secret) {
        if (secret == null || secret.length() < Constants.MIN_SECRET_LENGTH) {
            return MASK;
        }
        return MASK + secret.substring(secret.length() - 4);
    }
}
