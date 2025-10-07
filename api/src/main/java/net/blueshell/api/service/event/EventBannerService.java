package net.blueshell.api.service.event;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.event.EventBanner;
import net.blueshell.api.repository.EventBannerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EventBannerService extends BaseModelService<EventBanner, EventBannerRepository> {

    @Autowired
    public EventBannerService(
            EventBannerRepository repository
    ) {
        super(repository);
    }

}
