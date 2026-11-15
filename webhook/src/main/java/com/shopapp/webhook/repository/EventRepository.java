package com.shopapp.webhook.repository;

import com.shopapp.webhook.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}
