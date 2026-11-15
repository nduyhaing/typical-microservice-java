package com.shopapp.webhook.controller;

import com.shopapp.commonlibrary.constants.PageableConstant;
import com.shopapp.webhook.service.WebhookService;
import com.shopapp.webhook.utils.Constants;
import com.shopapp.webhook.viewmodel.TestDeliveryVm;
import com.shopapp.webhook.viewmodel.WebhookDetailVm;
import com.shopapp.webhook.viewmodel.WebhookListGetVm;
import com.shopapp.webhook.viewmodel.WebhookNotificationListVm;
import com.shopapp.webhook.viewmodel.WebhookPostVm;
import com.shopapp.webhook.viewmodel.WebhookVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** YAS WebhookController paths, plus the delivery log and a test delivery. ADMIN only (SecurityConfig). */
@RestController
@RequestMapping("/backoffice/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @GetMapping("/paging")
    public ResponseEntity<WebhookListGetVm> getPageableWebhooks(
        @RequestParam(value = "pageNo", defaultValue = PageableConstant.DEFAULT_PAGE_NUMBER, required = false)
        @Min(0) @Max(Constants.MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = PageableConstant.DEFAULT_PAGE_SIZE, required = false)
        @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(webhookService.getPageableWebhooks(pageNo, pageSize));
    }

    @GetMapping
    public ResponseEntity<List<WebhookVm>> listWebhooks() {
        return ResponseEntity.ok(webhookService.findAllWebhooks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WebhookDetailVm> getWebhook(@PathVariable("id") Long id) {
        return ResponseEntity.ok(webhookService.findById(id));
    }

    @PostMapping
    public ResponseEntity<WebhookDetailVm> createWebhook(@Valid @RequestBody WebhookPostVm webhookPostVm) {
        WebhookDetailVm webhook = webhookService.create(webhookPostVm);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(webhook.id()).toUri())
            .body(webhook);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateWebhook(@PathVariable("id") Long id,
                                              @Valid @RequestBody WebhookPostVm webhookPostVm) {
        webhookService.update(webhookPostVm, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWebhook(@PathVariable("id") Long id) {
        webhookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** The webhook's delivery log, newest first. */
    @GetMapping("/{id}/notifications")
    public ResponseEntity<WebhookNotificationListVm> getNotifications(
        @PathVariable("id") Long id,
        @RequestParam(value = "pageNo", defaultValue = PageableConstant.DEFAULT_PAGE_NUMBER, required = false)
        @Min(0) @Max(Constants.MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = PageableConstant.DEFAULT_PAGE_SIZE, required = false)
        @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(webhookService.getNotifications(id, pageNo, pageSize));
    }

    /** Queues a PING delivery (sent by the delivery worker; see the log for the outcome). */
    @PostMapping("/{id}/test")
    public ResponseEntity<TestDeliveryVm> sendTest(@PathVariable("id") Long id) {
        return ResponseEntity.accepted().body(webhookService.sendTest(id));
    }
}
