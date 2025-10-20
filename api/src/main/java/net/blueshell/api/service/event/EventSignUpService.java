package net.blueshell.api.service.event;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.controller.filter.EventSignUpFilter;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.repository.event.EventSignUpRepository;
import net.blueshell.api.repository.spec.EventSignUpSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

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
    public List<EventSignUp> findByGuestAccessToken(String accessToken) {
        return repository.findByGuestAccessToken(accessToken);
    }

    public List<EventSignUp> findByEventId(Long eventId) {
        return repository.findByEventId(eventId);
    }

    public List<EventSignUp> findByFilter(EventSignUpFilter filter) {
        if (filter == null) filter = new EventSignUpFilter();
        var spec = EventSignUpSpecifications.fromFilter(filter, getPrincipal());
        return repository.findAll(spec);
    }

    public Set<EventSignUp> findBySurveyId(Long surveyId) {
        return repository.findAllByEventSignUpFormId(surveyId);
    }

    public EventSignUp findByGuestAccessTokenAndEventId(String accessToken, Long eventId) {
        return repository.findByGuestAccessTokenAndEventId(accessToken, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "EventSignUp not found for accessToken: %s and event: %d".formatted(accessToken, eventId)));
    }
}
