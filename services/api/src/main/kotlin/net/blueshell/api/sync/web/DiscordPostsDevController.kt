package net.blueshell.api.sync.web

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.sync.domain.DiscordEventPostTriggers
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Runs the Discord morning run now rather than at 08:00, so a developer need not wait a day to
 * see what it posts. Only on the dev profile: production never has it.
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
    fun run(): Map<String, Int> = mapOf("events" to triggers.runMorning())
}
