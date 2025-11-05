package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.event.Guest;
import net.blueshell.api.repository.GuestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class GuestService extends BaseModelService<Guest, GuestRepository> {

    @Autowired
    public GuestService(GuestRepository repository, ApplicationEventPublisher events) {
        super(repository);
    }

    @Transactional(readOnly = true)
    public Optional<Guest> findByAccessToken(String accessToken) {
        return repository.findByAccessToken(accessToken);
    }
}
