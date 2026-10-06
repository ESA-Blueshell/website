package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.cohort.domain.DiscordPlace
import net.blueshell.api.cohort.domain.ServerCohortRole
import net.blueshell.api.cohort.domain.ServerCohortRoles
import net.blueshell.api.security.BoardOnly
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The Discord settings page's server-wide cohorts: the role each follows and the channels it opens. */
@RestController
@RequestMapping("/management/discord/settings/cohorts")
@Tag(name = "Discord settings", description = "The Discord roles and defaults the board sets for the whole server")
@BoardOnly
class DiscordSettingsController(
    private val roles: ServerCohortRoles,
) {
    @GetMapping
    fun listServerCohortRoles(): List<ServerCohortRole> = roles.read()

    @GetMapping("/{key}/discord")
    fun findServerCohortDiscord(
        @PathVariable key: String,
    ): DiscordPlace = roles.place(key)

    @PutMapping("/{key}/discord")
    fun setServerCohortDiscord(
        @PathVariable key: String,
        @RequestBody request: DiscordPlaceRequest,
    ): DiscordPlace = roles.apply(key, request.choice())
}
