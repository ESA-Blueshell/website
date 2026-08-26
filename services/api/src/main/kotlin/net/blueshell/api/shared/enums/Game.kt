package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

/**
 * A game the association fields teams in, present or past.
 *
 * Closed because each game has a page written for it, so adding one is a code change either
 * way. [CSGO] and [SMASH] no longer have a page: their teams are history, and history is
 * still worth reading back.
 */
@Schema(enumAsRef = true)
enum class Game(val label: String) {
    VALORANT("Valorant"),
    CS2("CS2"),
    CSGO("CS:GO"),
    LEAGUE_OF_LEGENDS("League of Legends"),
    ROCKET_LEAGUE("Rocket League"),
    TRACKMANIA("Trackmania"),
    GEOGUESSR("GeoGuessr"),
    SMASH("Super Smash Bros."),
}
