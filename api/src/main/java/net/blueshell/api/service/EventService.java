package net.blueshell.api.service;

import net.blueshell.api.model.*;
import net.blueshell.api.repository.EventRepository;
import net.blueshell.api.service.google.CalendarService;
import net.blueshell.api.base.BaseModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class EventService extends BaseModelService<Event, Long, EventRepository> {

    private final CalendarService calendarService;
    private final EventPictureService eventPictureService;

    @Autowired
    public EventService(EventRepository repository,
                        CalendarService calendarService, EventPictureService eventPictureService) {
        super(repository);
        this.calendarService = calendarService;
        this.eventPictureService = eventPictureService;
    }

    public List<Event> findUpcoming() {
        return repository.findUpcoming();
    }

    /**
     * Creates a new event.
     *
     * @return Created Event entity.
     * @throws IOException if calendar integration fails.
     */
    @Transactional
    public void createEvent(Event event) throws IOException {

        // Handle visibility and calendar integration
        if (event.isVisible()) {
            calendarService.addToGoogleCalendar(event);
        }

        // Save event to database
        create(event);
    }

    /**
     * Updates an existing event.
     *
     * @return Updated Event entity.
     * @throws IOException if calendar integration fails.
     */
    @Transactional
    public Event updateEvent(Event event) throws IOException {
        // Handle visibility and calendar integration
        if (event.isVisible()) {
            if (event.getGoogleId() != null) {
                calendarService.updateGoogleCalendar(event);
            } else {
                calendarService.addToGoogleCalendar(event);
            }
        } else if (event.getGoogleId() != null) {
            // Remove from calendar if no longer visible
            calendarService.removeFromGoogleCalendar(event);
        }

        // Save updated event to database
        return repository.save(event);
    }

    /**
     * Deletes an event by its ID.
     *
     * @param id Event ID.
     * @throws IOException if calendar integration fails.
     */

    @Transactional
    public void deleteEvent(Long id) throws IOException {
        Event event = findById(id);

        // Remove from calendar if present
        if (event.getGoogleId() != null) {
            calendarService.removeFromGoogleCalendar(event);
        }

        repository.delete(event);
    }

    public Event findByBanner(File banner) {
        return repository.findByBanner(banner);
    }


    public List<Event> findStartTimeBetween(LocalDateTime from, LocalDateTime to) {
        return repository.findStartTimeBetween(from, to);
    }
}
