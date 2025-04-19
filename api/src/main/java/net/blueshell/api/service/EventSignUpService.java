package net.blueshell.api.service;

import net.blueshell.api.common.exception.ResourceNotFoundException;
import net.blueshell.api.model.Event;
import net.blueshell.api.model.EventSignUp;
import net.blueshell.api.repository.EventSignUpRepository;
import net.blueshell.api.service.brevo.EmailService;
import net.blueshell.api.base.BaseModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sendinblue.ApiException;

import java.util.List;

@Service
public class EventSignUpService extends BaseModelService<EventSignUp, Long, EventSignUpRepository> {

    private final EventService eventService;
    private final EmailService emailService;

    @Autowired
    public EventSignUpService(EventSignUpRepository repository,
                              EventService eventService,
                              EmailService emailService) {
        super(repository);
        this.eventService = eventService;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public EventSignUp findByUserIdAndEventId(Long userId, Long eventId) {
        return repository.findByUserIdAndEventId(userId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EventSignUp not found for user: "
                        + userId + " and event: " + eventId));
    }

    @Transactional(readOnly = true)
    public EventSignUp findByGuestAccessToken(String accessToken) {
        return repository.findByGuestAccessToken(accessToken)
                .orElseThrow(() -> new ResourceNotFoundException("EventSignUp not found with accessToken: " + accessToken));
    }

    @Transactional
    public EventSignUp createSignUp(Long eventId, EventSignUp signUp) throws ApiException {
        Event event = eventService.findById(eventId);
        signUp.setEvent(event);
        if (signUp.getGuest() != null) {
            emailService.sendEventSignUpEmail(signUp);
        }
        create(signUp);
        return signUp;
    }

    @Transactional(readOnly = true)
    public void deleteSignUp(Long eventSignupId, String accessToken) {
        EventSignUp signUp;
        if (accessToken == null) {
            signUp = findById(eventSignupId);
        } else {
            signUp = findByGuestAccessToken(accessToken);
        }
        deleteById(signUp.getId());
    }

    public List<EventSignUp> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    public List<EventSignUp> findByEventId(Long eventId) {
        return repository.findByEventId(eventId);
    }
}
