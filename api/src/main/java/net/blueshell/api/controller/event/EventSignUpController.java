package net.blueshell.api.controller.event;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.controller.filter.EventSignUpFilter;
import net.blueshell.api.dto.event.EventSignUpDTO;
import net.blueshell.api.mapper.event.EventSignUpMapper;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.service.event.EventSignUpService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@Tag(name = "EventSignUps")
public class EventSignUpController extends BaseController<EventSignUpService, EventSignUpMapper> {
    @Autowired
    public EventSignUpController(EventSignUpService service, EventSignUpMapper mapper) {
        super(service, mapper);
    }

    @GetMapping(value = "/events/signups")
    @PreAuthorize("""
            hasAuthority('BOARD')
            || (#filter.userId != null && hasPermission(#filter.userId, 'User', 'read'))
            || (#filter.committeeId != null && hasPermission(#filter.committeeId, 'Committee', 'events'))
            """)
    public List<EventSignUpDTO> findEventSignUps(@ParameterObject EventSignUpFilter filter) {
        var eventSignUps = service.findByFilter(filter);
        return mapper.toDTOs(eventSignUps.stream()).toList();
    }

    @GetMapping(value = "/events/signups/byAccessToken/{accessToken}")
    @PreAuthorize("hasPermission(#accessToken, 'Guest', 'read')")
    public List<EventSignUpDTO> findEventSignUpsByAccessToken(@PathVariable("accessToken") String accessToken) {
        var signUps = service.findByGuestAccessToken(accessToken);
        return mapper.toDTOs(signUps);
    }

    @GetMapping(value = "/events/{eventId}/signups")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#eventId, 'Event', 'write')")
    public List<EventSignUpDTO> findEventSignUpsByEventId(@PathVariable("eventId") Long eventId) {
        var eventSignUps = service.findByEventId(eventId);
        return mapper.toDTOs(eventSignUps);
    }


    @PostMapping(value = "/events/{eventId}/signups")
    @PreAuthorize("#eventId == #dto.eventId && (hasAuthority('BOARD') or hasPermission(#eventId, 'Event', 'signUp'))")
    @ResponseStatus(HttpStatus.CREATED)
    public EventSignUpDTO createEventSignup(@PathVariable("eventId") Long eventId, @Valid @RequestBody EventSignUpDTO dto) {
        log.info("Get principal: {}", getPrincipal());
        if (getPrincipal() != null) {
            dto.setUserId(getPrincipal().getId());
        }
        var eventSignUp = mapper.fromDTO(dto);
        log.info("Creating event signup for event {}", eventSignUp);
        log.info("from dto {}", dto);
        log.info("guest dto {}", dto.getGuest());
        eventSignUp = service.create(eventSignUp);
        return mapper.toDTO(eventSignUp);
    }

    @PutMapping("/events/{eventId}/signups")
    @PreAuthorize("""
            hasAuthority('BOARD')
            or hasPermission(#eventId, 'Event', 'signUp')
            or (#accessToken != null and hasPermission(#accessToken, 'Guest', 'write'))
            """)
    public EventSignUpDTO updateEventSignUp(
            @PathVariable("eventId") Long eventId,
            @Valid @RequestBody EventSignUpDTO dto,
            @RequestParam(value = "accessToken", required = false) String accessToken) {
        EventSignUp signUp = (accessToken == null)
                ? service.findByUserIdAndEventId(getPrincipal().getId(), eventId)
                : service.findByGuestAccessTokenAndEventId(accessToken, eventId);
        mapper.fromDTO(dto, signUp);
        signUp = service.update(signUp);
        return mapper.toDTO(signUp);
    }


    @DeleteMapping(value = "/events/signups/{eventSignupId}")
    @PreAuthorize("""
            hasAuthority('BOARD')
            or hasPermission(#eventSignupId, 'EventSignUp', 'delete')
            or hasPermission(#accessToken, 'Guest', 'delete')
            """)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEventSignup(@PathVariable("eventSignupId") Long eventSignupId,
                                  @RequestParam(value = "accessToken", required = false) String accessToken) {
        service.deleteById(eventSignupId);
    }
}
