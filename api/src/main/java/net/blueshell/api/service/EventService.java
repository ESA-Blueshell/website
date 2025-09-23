package net.blueshell.api.service;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.controller.filter.EventFilter;
import net.blueshell.api.model.Event;
import net.blueshell.api.model.File;
import net.blueshell.api.repository.EventRepository;
import net.blueshell.api.repository.spec.EventSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EventService extends BaseModelService<Event, Long, EventRepository> {

    @Autowired
    public EventService(EventRepository repository) {
        super(repository);
    }

    public Event findByBanner(File banner) {
        return repository.findByBanner(banner);
    }

    public Page<Event> findByFilter(Pageable pageable, EventFilter filter) {
        if (filter == null) filter = new EventFilter();
        if (pageable == null) pageable = Pageable.unpaged();
        var spec = EventSpecifications.fromFilter(filter, getPrincipal());
        return repository.findAll(spec, pageable);
    }
}
