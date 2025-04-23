package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.exception.ResourceNotFoundException;
import net.blueshell.api.model.Guest;
import net.blueshell.api.repository.GuestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestService extends BaseModelService<Guest, Long, GuestRepository> {

    @Autowired
    public GuestService(GuestRepository repository) {
        super(repository);
    }

    @Transactional(readOnly = true)
    public Guest findByAccessToken(String accessToken) {
        return repository.findByAccessToken(accessToken)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found with accessToken: " + accessToken));
    }
}
