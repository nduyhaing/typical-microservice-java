package com.shopapp.webhook.viewmodel;

import com.shopapp.webhook.utils.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * YAS WebhookPostVm (create and update).
 *
 * @param payloadUrl  http(s) URL of a public host (SSRF guard, decisions.md 5-33)
 * @param secret      HMAC key, at least 16 characters; required on create, null or empty on update keeps it
 * @param contentType application/json (the default when empty), the only type sent
 * @param isActive    null: true on create, unchanged on update
 * @param events      the subscribed events (by id)
 */
public record WebhookPostVm(
    @NotBlank @Size(max = Constants.MAX_URL_LENGTH) String payloadUrl,
    @Size(max = Constants.MAX_SECRET_LENGTH) String secret,
    @Size(max = 128) String contentType,
    Boolean isActive,
    @Size(max = 10) List<EventVm> events) {
}
