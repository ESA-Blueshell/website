package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.event.domain.RosterPerson

@Schema(description = "Who signed up for an event, as its page shows it")
data class EventRosterResponse(
    @field:Schema(description = "The people signed up with an account, first sign-up first")
    val people: List<RosterPersonResponse>,
    @field:Schema(description = "How many guests signed up without an account; they are counted, never named")
    val guests: Int,
)

@Schema(description = "One person on an event's roster")
data class RosterPersonResponse(
    @field:Schema(description = "Their Discord name where Discord is linked, otherwise their username")
    val name: String,
    @field:Schema(description = "Their Discord picture; absent where Discord is not linked")
    val avatar: String?,
    @field:Schema(description = "Whether the name is their Discord name")
    val discord: Boolean,
) {
    constructor(person: RosterPerson) : this(person.name, person.avatar, person.discord)
}
