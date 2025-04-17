package net.blueshell.telemetry.service;

import net.blueshell.db.BaseModelService;
import net.blueshell.telemetry.model.Redirect;
import net.blueshell.telemetry.model.Telemetry;
import net.blueshell.telemetry.repository.RedirectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
public class RedirectService extends BaseModelService<Redirect, UUID, RedirectRepository> {
    private final TelemetryService telemetryService;

    @Autowired
    public RedirectService(RedirectRepository repository, TelemetryService telemetryService) {
        super(repository);
        this.telemetryService = telemetryService;
    }

    @Transactional
    public String createRedirect(UUID telemetryId) {
        Telemetry telemetry = telemetryService.findById(telemetryId);
        Redirect newRedirect = new Redirect(telemetry);
        create(newRedirect);
        return "redirect:" + telemetry.getUrl();
    }

    public List<Redirect> findCreatedAtBetween(OffsetDateTime from, OffsetDateTime to) {
        return repository.findCreatedAtBetween(from, to);
    }
}
