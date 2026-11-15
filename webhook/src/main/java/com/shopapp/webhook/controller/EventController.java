package com.shopapp.webhook.controller;

import com.shopapp.webhook.service.EventService;
import com.shopapp.webhook.viewmodel.EventVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backoffice/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventVm>> listEvents() {
        return ResponseEntity.ok(eventService.findAllEvents());
    }
}
