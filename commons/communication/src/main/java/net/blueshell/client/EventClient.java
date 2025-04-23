package net.blueshell.client;

import net.blueshell.dto.EventDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

@FeignClient(
        name = "API",
        contextId = "eventClient",
        path="/events"
)
public interface EventClient {

    @PostMapping
    EventDTO createEvent(@RequestBody EventDTO eventDTO) throws Exception;

    @GetMapping("/{id}")
    EventDTO getEventById(@PathVariable("id") Long id);

    @GetMapping
    List<EventDTO> getEvents(@RequestParam(value = "from", required = false) OffsetDateTime from,
                             @RequestParam(value = "to", required = false) OffsetDateTime to);

    @GetMapping("/upcoming")
    List<EventDTO> getUpcomingEvents(@RequestParam(value = "editable", defaultValue = "false") boolean editable);

    @GetMapping("/past")
    Stream<EventDTO> getPastEvents(@RequestParam(value = "editable", defaultValue = "false") boolean editable);

    @GetMapping("/pageable")
    Page<EventDTO> getEventsPageable(@SpringQueryMap Pageable pageable);

    @PutMapping("/{eventId}")
    EventDTO updateEvent(@PathVariable("eventId") Long eventId,
                         @RequestBody EventDTO dto) throws Exception;

    @DeleteMapping("/{eventId}")
    void deleteEventById(@PathVariable("eventId") Long eventId) throws Exception;
}
