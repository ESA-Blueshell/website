package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.exception.ResourceNotFoundException;
import net.blueshell.api.model.Event;
import net.blueshell.api.model.EventPicture;
import net.blueshell.api.model.EventSignUp;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.EventPictureRepository;
import net.blueshell.api.repository.EventSignUpRepository;
import net.blueshell.api.service.brevo.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sendinblue.ApiException;

import java.util.List;

@Service
public class EventPictureService extends BaseModelService<EventPicture, Long, EventPictureRepository> {

    @Autowired
    public EventPictureService(EventPictureRepository repository) {
        super(repository);
    }
}
