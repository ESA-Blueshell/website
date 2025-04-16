package net.blueshell.telemetry.controller;

import jakarta.ws.rs.PathParam;
import net.blueshell.common.dto.RedirectDTO;
import net.blueshell.db.BaseController;
import net.blueshell.telemetry.mapping.RedirectMapper;
import net.blueshell.telemetry.model.Redirect;
import net.blueshell.telemetry.service.RedirectService;
import net.blueshell.telemetry.service.TelemetryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class RedirectController extends BaseController<RedirectService, RedirectMapper> {

    protected RedirectController(RedirectService service, RedirectMapper mapper, TelemetryService telemetryService) {
        super(service, mapper);
    }

    @RequestMapping("/redirect")
    public String redirect(@PathParam("id") UUID telemetryId) {
        return service.createRedirect(telemetryId);
    }

    @GetMapping("/redirects")
    public List<RedirectDTO> getRedirects(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime to
    ) {
        List<Redirect> redirects = service.findCreatedAtBetween(from, to);
        return mapper.toDTOs(redirects);
    }
}

