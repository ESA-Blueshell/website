package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.Event;
import net.blueshell.api.model.File;
import net.blueshell.api.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventService extends BaseModelService<Event, Long, EventRepository> {

    @Autowired
    public EventService(EventRepository repository, ApplicationEventPublisher events) {
        super(repository, events);
    }

    public List<Event> findUpcoming() {
        return repository.findUpcoming();
    }

    public Event findByBanner(File banner) {
        return repository.findByBanner(banner);
    }

    public List<Event> findStartTimeBetween(LocalDateTime from, LocalDateTime to) {
        return repository.findStartTimeBetween(from, to);
    }
}
