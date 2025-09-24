package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.common.enums.PlatformType;
import net.blueshell.api.model.Telemetry;
import net.blueshell.api.repository.TelemetryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TelemetryService extends BaseModelService<Telemetry, TelemetryRepository> {

    @Autowired
    public TelemetryService(TelemetryRepository repository, ApplicationEventPublisher events) {
        super(repository);
    }

    @Transactional
    public Telemetry createTelemetry(PlatformType platform, String url) {
        Telemetry telemetry = new Telemetry(platform, url);
        create(telemetry);
        return telemetry;
    }
}
