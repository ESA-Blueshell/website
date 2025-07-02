package net.blueshell.api.controller;

import net.blueshell.api.mapper.RedirectMapper;
import net.blueshell.api.model.Redirect;
import net.blueshell.api.dto.RedirectDTO;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.service.RedirectService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class RedirectController extends BaseController<RedirectService, RedirectMapper> {

    protected RedirectController(RedirectService service, RedirectMapper mapper) {
        super(service, mapper);
    }

    @PostMapping("/telemetry/redirect")
    public String addRedirect(@RequestParam("id") String telemetryId) {
        return service.createRedirect(UUID.fromString(telemetryId));
    }

    @DeleteMapping("/telemetry/redirect")
    public void deleteRedirect(@RequestParam("id") String telemetryId) {
        service.delete(UUID.fromString(telemetryId));
    }

    @GetMapping("/telemetry/redirects")
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

    @GetMapping("/telemetry/redirects/dashboard")
    public ModelAndView getDashboard() {
        return new ModelAndView("index");
    }
}

