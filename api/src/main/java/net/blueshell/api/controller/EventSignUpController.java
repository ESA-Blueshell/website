package net.blueshell.api.controller;

import jakarta.validation.Valid;
import jakarta.ws.rs.NotFoundException;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.EventSignUpDTO;
import net.blueshell.api.mapper.EventSignUpMapper;
import net.blueshell.api.model.EventSignUp;
import net.blueshell.api.model.User;
import net.blueshell.api.service.EventSignUpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sendinblue.ApiException;

import java.util.List;
import java.util.stream.Stream;

@RestController
public class EventSignUpController extends BaseController<EventSignUpService, EventSignUpMapper> {
    @Autowired
    public EventSignUpController(
            EventSignUpService service,
            EventSignUpMapper mapper) {
        super(service, mapper);
    }

    @GetMapping(value = "/events/signups")
    @PreAuthorize("principal != null")
    public List<EventSignUpDTO> getMySignUps() {
        User user = getPrincipal();
        if (user == null) {
            throw new NotFoundException();
        }

        List<EventSignUp> eventSignUps = service.findByUserId(user.getId());
        return mapper.toDTOs(eventSignUps.stream()).toList();
    }

    @GetMapping(value = "/events/signups/byAccessToken/{accessToken}")
    @PreAuthorize("hasPermission(#accessToken, 'Guest', 'see')")
    public EventSignUpDTO getSignUpByAccessToken(@PathVariable("accessToken") String accessToken) {
        EventSignUp signUp = service.findByGuestAccessToken(accessToken);
        return mapper.toDTO(signUp);
    }

    @GetMapping(value = "/events/{id}/signups")
    @PreAuthorize("hasPermission(#eventId, 'Event', 'seeSignUps')")
    public Stream<EventSignUpDTO> getAllSignUps(@PathVariable("id") Long eventId) {
        List<EventSignUp> eventSignUps = service.findByEventId(eventId);
        return mapper.toDTOs(eventSignUps.stream());
    }


    @PostMapping(value = "/events/{id}/signups")
    @PreAuthorize("hasPermission(#eventId, 'Event', 'signUp')")
    public EventSignUpDTO createSignup(@PathVariable("id") Long eventId,
                                       @Valid @RequestBody EventSignUpDTO dto) throws ApiException {
        dto.setEventId(eventId);
        EventSignUp eventSignUp = mapper.fromDTO(dto);
        EventSignUp signUp = service.createSignUp(eventId, eventSignUp);
        return mapper.toDTO(signUp);
    }

    @PutMapping(value = "/events/{eventId}/signups")
    @PreAuthorize("hasPermission(#eventId, 'Event', 'signUp') or hasPermission(accessToken, 'Guest', 'delete')")
    public EventSignUpDTO updateSignUp(@PathVariable("eventId") Long eventId,
                                       @Valid @RequestBody EventSignUpDTO dto,
                                       @RequestParam(value = "accessToken", required = false) String accessToken) {
        long signUpId;
        if (accessToken == null) {
            signUpId = service.findByUserIdAndEventId(getPrincipal().getId(), eventId).getId();
        } else {
            signUpId = service.findByGuestAccessToken(accessToken).getId();
        }
        EventSignUp signUp = mapper.fromDTO(dto);
        signUp.setId(signUpId);
        service.update(signUp);
        return mapper.toDTO(signUp);
    }

    @DeleteMapping(value = "/events/signups/{eventSignupId}")
    @PreAuthorize("hasPermission(#eventSignupId, 'EventSignUp', 'delete') or hasPermission(#accessToken, 'Guest', 'delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSignup(@PathVariable("eventSignupId") Long eventSignupId,
                             @RequestParam(value = "accessToken", required = false) String accessToken) {
        service.deleteSignUp(eventSignupId, accessToken);
    }
}
