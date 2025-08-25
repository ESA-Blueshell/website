package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.EventPicture;
import net.blueshell.api.repository.EventPictureRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class EventPictureService extends BaseModelService<EventPicture, Long, EventPictureRepository> {

    @Autowired
    public EventPictureService(EventPictureRepository repository, ApplicationEventPublisher events) {
        super(repository);
    }
}
