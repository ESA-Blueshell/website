package net.blueshell.api.game.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.file.api.Image
import net.blueshell.api.file.api.asImage
import net.blueshell.api.game.persistence.Game

@Schema(description = "A game as the casual pages show it")
data class CasualGameResponse(
    @Schema(description = "The identifier everything else files the game under. Never changes")
    val code: String,
    @Schema(description = "What this game is called")
    val name: String,
    @Schema(description = "The address this game answers to under /casual")
    val slug: String,
    @Schema(description = "The colour that carries this game, where one has been chosen")
    val accent: String?,
    @Schema(description = "What is said about the game, where anything is said")
    val intro: String?,
    @Schema(description = "The game's own image")
    val banner: Image?,
    @Schema(description = "The game's own icon")
    val icon: Image?,
    @Schema(description = "Where the game sits among the others")
    val sortIndex: Int,
    @Schema(description = "Nobody plays it casually any more; it is among the games we used to play")
    val archived: Boolean,
    @Schema(description = "A team is fielded in it this season")
    val inCompetition: Boolean,
)

fun Game.asCasualResponse(inCompetition: Boolean): CasualGameResponse =
    CasualGameResponse(
        code = code,
        name = name,
        slug = slug,
        accent = accent,
        intro = intro,
        banner = banner?.asImage(),
        icon = icon?.asImage(),
        sortIndex = sortIndex,
        archived = archived,
        inCompetition = inCompetition,
    )
