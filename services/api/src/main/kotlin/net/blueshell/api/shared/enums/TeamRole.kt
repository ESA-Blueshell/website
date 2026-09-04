package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

/** What somebody does on a team's roster. */
@Schema(enumAsRef = true)
enum class TeamRole {
    PLAYER,
    SUBSTITUTE,
    COACH,
}
