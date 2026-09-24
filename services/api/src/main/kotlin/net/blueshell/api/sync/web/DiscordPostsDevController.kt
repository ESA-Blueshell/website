package net.blueshell.api.sync.web

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.sync.domain.DiscordEventPostTriggers
import org.springframework.context.annotation.Profile
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Runs the Discord morning run now, as if it were [at] in Amsterdam, so a developer can walk every
 * post, edit and removal through in minutes. Only on the dev profile: production never has it.
 */
@RestController
@RequestMapping("/dev/discord-posts")
@Profile("dev")
@Hidden
@Tag(name = "Dev")
class DiscordPostsDevController(
    private val triggers: DiscordEventPostTriggers,
) {
    @PermitAll
    @GetMapping("/run")
    fun run(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) at: LocalDateTime,
    ): Map<String, Int> = mapOf("events" to triggers.runMorning(at.atZone(ZoneId.of("Europe/Amsterdam")).toInstant()))
}
