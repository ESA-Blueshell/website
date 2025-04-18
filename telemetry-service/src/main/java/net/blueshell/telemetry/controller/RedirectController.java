package net.blueshell.telemetry.controller;

import net.blueshell.common.dto.RedirectDTO;
import net.blueshell.db.BaseController;
import net.blueshell.telemetry.mapping.RedirectMapper;
import net.blueshell.telemetry.model.Redirect;
import net.blueshell.telemetry.service.RedirectService;
import net.blueshell.telemetry.service.TelemetryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class RedirectController extends BaseController<RedirectService, RedirectMapper> {

    protected RedirectController(RedirectService service, RedirectMapper mapper, TelemetryService telemetryService) {
        super(service, mapper);
    }

    @PostMapping("/redirect")
    public String addRedirect(@RequestParam("id") String telemetryId) {
        return service.createRedirect(UUID.fromString(telemetryId));
    }

    @DeleteMapping("/redirect")
    public void deleteRedirect(@RequestParam("id") String telemetryId) {
        service.deleteById(UUID.fromString(telemetryId));
    }

    @GetMapping("/redirects")
    public List<RedirectDTO> getRedirects(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime to
    ) {
        List<Redirect> redirects;
        if(from != null || to != null) {
             redirects = service.findCreatedAtBetween(from, to);
        } else {
            redirects = service.findAll();
        }

        return mapper.toDTOs(redirects);
    }

    @GetMapping("/redirects/dashboard")
    public ModelAndView getDashboard() {
        return new ModelAndView("index");
    }

}

