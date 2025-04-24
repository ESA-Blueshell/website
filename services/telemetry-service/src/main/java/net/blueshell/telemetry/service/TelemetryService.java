package net.blueshell.telemetry.service;

import net.blueshell.enums.PlatformType;
import net.blueshell.db.BaseModelService;
import net.blueshell.telemetry.model.Telemetry;
import net.blueshell.telemetry.repository.TelemetryRepository;
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
