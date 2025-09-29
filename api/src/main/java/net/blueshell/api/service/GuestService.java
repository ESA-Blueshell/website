package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.Guest;
import net.blueshell.api.repository.GuestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GuestService extends BaseModelService<Guest, GuestRepository> {

    @Autowired
    public GuestService(GuestRepository repository, ApplicationEventPublisher events) {
        super(repository);
    }

    @Transactional(readOnly = true)
    public Guest findByAccessToken(String accessToken) {
        return repository.findByAccessToken(accessToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found with accessToken: %s".formatted(accessToken)));
    }
}
