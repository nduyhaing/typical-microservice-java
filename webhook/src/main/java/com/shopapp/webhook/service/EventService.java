package com.shopapp.webhook.service;

import com.shopapp.webhook.repository.EventRepository;
import com.shopapp.webhook.viewmodel.EventVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    /** All events, by id (YAS: id descending). */
    @Transactional(readOnly = true)
    public List<EventVm> findAllEvents() {
        return eventRepository.findAll(Sort.by("id")).stream().map(EventVm::fromModel).toList();
    }
}
