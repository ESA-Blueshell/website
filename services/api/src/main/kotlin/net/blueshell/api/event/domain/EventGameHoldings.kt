package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.game.api.GameHeld
import net.blueshell.api.game.api.GameHoldings
import org.springframework.stereotype.Component

/** The events that name a game, counted for its removal, which they never stop: they stop naming it. */
@Component
class EventGameHoldings(
    private val events: EventRepository,
) : GameHoldings {
    override fun heldAgainst(code: String): GameHeld = GameHeld(events = events.countNamingGame(code))
}
