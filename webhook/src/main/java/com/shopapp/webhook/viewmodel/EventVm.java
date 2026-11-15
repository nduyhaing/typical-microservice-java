package com.shopapp.webhook.viewmodel;

import com.shopapp.webhook.model.Event;
import com.shopapp.webhook.model.EventName;

/** YAS EventVm: an event; in a WebhookPostVm only the id is read. */
public record EventVm(Long id, EventName name) {

    public static EventVm fromModel(Event event) {
        return new EventVm(event.getId(), event.getName());
    }
}
