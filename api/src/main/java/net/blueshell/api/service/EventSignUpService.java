package net.blueshell.api.service;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.EventSignUp;
import net.blueshell.api.repository.EventSignUpRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
public class EventSignUpService extends BaseModelService<EventSignUp, EventSignUpRepository> {

    @Autowired
    public EventSignUpService(EventSignUpRepository repository) {
        super(repository);
    }

    @Transactional(readOnly = true)
    public EventSignUp findByUserIdAndEventId(Long userId, Long eventId) {
        return repository.findByUserIdAndEventId(userId, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "EventSignUp not found for user: %d and event: %d".formatted(userId, eventId)));
    }

    @Transactional(readOnly = true)
    public EventSignUp findByGuestAccessToken(String accessToken) {
        return repository.findByGuestAccessToken(accessToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "EventSignUp not found for accessToken: %s".formatted(accessToken)));
    }

    @Transactional(readOnly = true)
    public void deleteSignUp(Long eventSignupId, String accessToken) {
        EventSignUp signUp;
        if (accessToken == null) {
            signUp = self().findById(eventSignupId);
        } else {
            signUp = findByGuestAccessToken(accessToken);
        }
        self().delete(signUp.getId());
    }

    public List<EventSignUp> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    public List<EventSignUp> findByEventId(Long eventId) {
        return repository.findByEventId(eventId);
    }
}
