package net.blueshell.telemetry.service;

import net.blueshell.db.BaseModelService;
import net.blueshell.telemetry.model.Redirect;
import net.blueshell.telemetry.model.Telemetry;
import net.blueshell.telemetry.repository.RedirectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
public class RedirectService extends BaseModelService<Redirect, UUID, RedirectRepository> {
    private final TelemetryService telemetryService;
    private final WebSocketMessageService webSocketMessageService;
    Logger logger = LoggerFactory.getLogger(RedirectService.class);

    @Autowired
    public RedirectService(RedirectRepository repository, TelemetryService telemetryService,
                           WebSocketMessageService webSocketMessageService) {
        super(repository);
        this.telemetryService = telemetryService;
        this.webSocketMessageService = webSocketMessageService;
    }

    @Transactional
    public String createRedirect(UUID telemetryId) {
        logger.info("TL Creating redirect for telemetry");
        Telemetry telemetry = telemetryService.findById(telemetryId);
        Redirect newRedirect = new Redirect(telemetry);
        logger.info("TL Created redirect for telemetry: {}", telemetry.getId());
        create(newRedirect);

        List<Redirect> redirects = findAll();
        logger.info("TL Found all redirects");
        webSocketMessageService.sendMessage("/clicks/updates", redirects);

        return "redirect:" + telemetry.getUrl();
    }

    public List<Redirect> findCreatedAtBetween(OffsetDateTime from, OffsetDateTime to) {
        return repository.findCreatedAtBetween(from, to);
    }
}
