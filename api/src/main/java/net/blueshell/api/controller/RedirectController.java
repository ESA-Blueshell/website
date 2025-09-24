package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.RedirectDTO;
import net.blueshell.api.mapper.RedirectMapper;
import net.blueshell.api.model.Redirect;
import net.blueshell.api.service.RedirectService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Redirects")
public class RedirectController extends BaseController<RedirectService, RedirectMapper> {

    protected RedirectController(RedirectService service, RedirectMapper mapper) {
        super(service, mapper);
    }

    @PostMapping("/telemetry/redirect")
    @PreAuthorize("hasAuthority('BOARD')")
    public String createRedirect(@RequestParam("id") Long telemetryId) {
        return service.createRedirect(telemetryId);
    }

    @DeleteMapping("/telemetry/redirect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('BOARD')")
    public void deleteRedirect(@RequestParam("id") Long telemetryId) {
        service.delete(telemetryId);
    }

    @GetMapping("/telemetry/redirects")
    @PreAuthorize("hasAuthority('BOARD')")
    public List<RedirectDTO> findRedirects(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime to
    ) {
        List<Redirect> redirects;
        if (from != null || to != null) {
            redirects = service.findCreatedAtBetween(from, to);
        } else {
            redirects = service.findAll();
        }

        return mapper.toDTOs(redirects);
    }

    @GetMapping("/telemetry/redirects/dashboard")
    @PreAuthorize("hasAuthority('BOARD')")
    public ModelAndView getDashboard() {
        return new ModelAndView("index");
    }
}

