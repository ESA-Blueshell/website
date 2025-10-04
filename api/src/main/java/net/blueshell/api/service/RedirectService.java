package net.blueshell.api.service;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.Redirect;
import net.blueshell.api.model.Telemetry;
import net.blueshell.api.repository.RedirectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Slf4j
public class RedirectService extends BaseModelService<Redirect, RedirectRepository> {
    private final TelemetryService telemetryService;
    private final WebSocketMessageService webSocketMessageService;

    @Autowired
    public RedirectService(RedirectRepository repository, TelemetryService telemetryService,
                           WebSocketMessageService webSocketMessageService) {
        super(repository);
        this.telemetryService = telemetryService;
        this.webSocketMessageService = webSocketMessageService;
    }

    @Transactional
    public String createRedirect(Long telemetryId) {
        log.info("TL Creating redirect for telemetry");
        Telemetry telemetry = telemetryService.findById(telemetryId);
        Redirect newRedirect = new Redirect(telemetry);
        log.info("TL Created redirect for telemetry: {}", telemetry.getId());
        self().create(newRedirect);

        List<Redirect> redirects = self().findAll();
        log.info("TL Found all redirects");
        webSocketMessageService.sendMessage("/clicks/updates", redirects);

        return "redirect:%s".formatted(telemetry.getUrl());
    }

    public List<Redirect> findCreatedAtBetween(OffsetDateTime from, OffsetDateTime to) {
        return repository.findCreatedAtBetween(from, to);
    }
}
