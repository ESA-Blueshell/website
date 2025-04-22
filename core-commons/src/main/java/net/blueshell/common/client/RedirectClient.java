package net.blueshell.common.client;

import net.blueshell.common.dto.RedirectDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "TelemetryService",
        contextId = "redirectClient"
)
public interface RedirectClient {

    @PostMapping("/telemetry/redirect")
    String addRedirect(@RequestParam("id") UUID telemetryId);

    @DeleteMapping("/telemetry/redirect")
    void deleteRedirect(@RequestParam("id") UUID telemetryId);

    @GetMapping("/telemetry/redirects")
    List<RedirectDTO> getRedirects(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to
    );

    @GetMapping("/telemetry/redirects/dashboard")
    ModelAndView getDashboard();
}
