package net.blueshell.common.client;

import net.blueshell.common.dto.EventDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

@FeignClient(name = "API")
public interface EventClient {

    @PostMapping("/events")
    EventDTO createEvent(@RequestBody EventDTO eventDTO) throws Exception;

    @GetMapping("/events/{id}")
    EventDTO getEventById(@PathVariable("id") Long id);

    @GetMapping("/events")
    List<EventDTO> getEvents(@RequestParam(value = "from", required = false) OffsetDateTime from,
                             @RequestParam(value = "to", required = false) OffsetDateTime to);

    @GetMapping("/events/upcoming")
    List<EventDTO> getUpcomingEvents(@RequestParam(value = "editable", defaultValue = "false") boolean editable);

    @GetMapping("/events/past")
    Stream<EventDTO> getPastEvents(@RequestParam(value = "editable", defaultValue = "false") boolean editable);

    @GetMapping("/events/pageable")
    Page<EventDTO> getEventsPageable(@SpringQueryMap Pageable pageable);

    @PutMapping("/events/{eventId}")
    EventDTO updateEvent(@PathVariable("eventId") Long eventId,
                         @RequestBody EventDTO dto) throws Exception;

    @DeleteMapping("/events/{eventId}")
    void deleteEventById(@PathVariable("eventId") Long eventId) throws Exception;
}
