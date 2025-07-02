package net.blueshell.api.service;

import net.blueshell.api.common.exception.ResourceNotFoundException;
import net.blueshell.api.model.Guest;
import net.blueshell.api.repository.GuestRepository;
import net.blueshell.api.base.BaseModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestService extends BaseModelService<Guest, Long, GuestRepository> {

    @Autowired
    public GuestService(GuestRepository repository, ApplicationEventPublisher events) {
        super(repository, events);
    }

    @Transactional(readOnly = true)
    public Guest findByAccessToken(String accessToken) {
        return repository.findByAccessToken(accessToken)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found with accessToken: " + accessToken));
    }
}
