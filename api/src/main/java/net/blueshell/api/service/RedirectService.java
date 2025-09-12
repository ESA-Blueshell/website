package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.Redirect;
import net.blueshell.api.model.Telemetry;
import net.blueshell.api.repository.RedirectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RedirectService extends BaseModelService<Redirect, UUID, RedirectRepository> {
    private final TelemetryService telemetryService;
    private final WebSocketMessageService webSocketMessageService;
    final Logger logger = LoggerFactory.getLogger(RedirectService.class);

    @Autowired
    public RedirectService(RedirectRepository repository,  TelemetryService telemetryService,
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
        self().create(newRedirect);

        List<Redirect> redirects = self().findAll();
        logger.info("TL Found all redirects");
        webSocketMessageService.sendMessage("/clicks/updates", redirects);

        return "redirect:" + telemetry.getUrl();
    }

    public List<Redirect> findCreatedAtBetween(OffsetDateTime from, OffsetDateTime to) {
        return repository.findCreatedAtBetween(from, to);
    }
}
