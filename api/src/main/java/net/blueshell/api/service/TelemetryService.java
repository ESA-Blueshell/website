package net.blueshell.api.service;

import net.blueshell.api.common.enums.PlatformType;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.Telemetry;
import net.blueshell.api.repository.TelemetryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@org.springframework.stereotype.Service
public class TelemetryService extends BaseModelService<Telemetry, UUID, TelemetryRepository> {

    @Autowired
    public TelemetryService(TelemetryRepository repository) {
        super(repository);
    }

    @Transactional
    public Telemetry createTelemetry(PlatformType platform, String url) {
        Telemetry telemetry = new Telemetry(platform, url);
        create(telemetry);
        return telemetry;
    }
}
