package net.blueshell.api.controller;

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
    public EventSignUpDTO findEventSignUpByAccessToken(@PathVariable("accessToken") String accessToken) {
        var signUp = service.findByGuestAccessToken(accessToken);
        return mapper.toDTO(signUp);
    }

    @GetMapping(value = "/events/{eventId}/signups")
    @PreAuthorize("hasPermission(#eventId, 'Event', 'seeSignUps')")
    public List<EventSignUpDTO> findEventSignUpsByEventId(@PathVariable("eventId") Long eventId) {
        var eventSignUps = service.findByEventId(eventId);
        return mapper.toDTOs(eventSignUps);
    }


    @PostMapping(value = "/events/{eventId}/signups")
    @PreAuthorize("hasPermission(#eventId, 'Event', 'signUp')")
    @ResponseStatus(HttpStatus.CREATED)
    public EventSignUpDTO createEventSignup(@PathVariable("eventId") Long eventId, @Valid @RequestBody EventSignUpDTO dto) {
        dto.setEventId(eventId);
        dto.setUserId(getPrincipal().getId());
        var eventSignUp = mapper.fromDTO(dto);
        eventSignUp = service.create(eventSignUp);
        return mapper.toDTO(eventSignUp);
    }

    @PutMapping(value = "/events/{eventId}/signups")
    @PreAuthorize("hasPermission(#eventId, 'Event', 'signUp') or hasPermission(accessToken, 'Guest', 'delete')")
    public EventSignUpDTO updateEventSignUp(@PathVariable("eventId") Long eventId,
                                            @Valid @RequestBody EventSignUpDTO dto,
                                            @RequestParam(value = "accessToken", required = false) String accessToken) {
        EventSignUp signUp;
        if (accessToken == null) {
            signUp = service.findByUserIdAndEventId(getPrincipal().getId(), eventId);
        } else {
            signUp = service.findByGuestAccessToken(accessToken);
        }
        mapper.fromDTO(dto, signUp);
        signUp = service.update(signUp);
        return mapper.toDTO(signUp);
    }

    @DeleteMapping(value = "/events/signups/{eventSignupId}")
    @PreAuthorize("hasPermission(#eventSignupId, 'EventSignUp', 'delete') or hasPermission(#accessToken, 'Guest', 'delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEventSignup(@PathVariable("eventSignupId") Long eventSignupId,
                                  @RequestParam(value = "accessToken", required = false) String accessToken) {
        service.delete(eventSignupId);
    }
}
