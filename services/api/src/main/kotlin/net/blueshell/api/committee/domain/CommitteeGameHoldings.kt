package net.blueshell.api.committee.domain

import net.blueshell.api.committee.persistence.CommitteeRepository
import net.blueshell.api.game.api.GameHoldings
import org.springframework.stereotype.Component

/** The committees that organise events for a game, counted for its removal, which they never stop. */
@Component
class CommitteeGameHoldings(
    private val committees: CommitteeRepository,
) : GameHoldings {
    override fun heldAgainst(code: String): Map<String, Long> = mapOf("committees" to committees.countNamingGame(code))
}
