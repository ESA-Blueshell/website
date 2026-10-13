package net.blueshell.api.alerts.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.alerts.persistence.HiddenAlertRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock

/** An alert as one reader sees it. */
data class ReaderAlert(
    val alert: RaisedAlert,
    val hidden: Boolean,
)

/** Gathers the alerts a reader may act on, and keeps what each person hid. */
@Service
class Alerts(
    private val sources: List<AlertSource>,
    private val hidden: HiddenAlertRepository,
    private val clock: Clock,
) {
    /**
     * Every alert this reader's roles can act on, the one that began last first. A hide is deleted
     * once its alert is no longer raised, so the next time the alert is raised it shows.
     */
    @Transactional
    fun forReader(reader: CurrentUser): List<ReaderAlert> {
        val raised = raisedFor(reader)
        val hides = hidden.findAllByUserId(reader.id).associate { it.alertKey to it.mark }
        val cleared = hides.keys - raised.map { it.key }.toSet()
        // The sidebar reads this on every page, so it writes only when there is a hide to forget.
        if (cleared.isNotEmpty()) hidden.forget(reader.id, cleared)
        return raised.map { alert -> ReaderAlert(alert, hides[alert.key]?.let { it >= alert.mark } ?: false) }
    }

    /** Hides one alert this reader can see, for them only. */
    @Transactional
    fun hide(
        reader: CurrentUser,
        key: String,
    ) {
        val alert = raisedFor(reader).firstOrNull { it.key == key } ?: throw notRaised()
        hidden.hide(reader.id, key, alert.mark, clock.instant())
    }

    /** Shows a hidden alert to this reader again. */
    @Transactional
    fun show(
        reader: CurrentUser,
        key: String,
    ) {
        hidden.show(reader.id, key)
    }

    private fun raisedFor(reader: CurrentUser): List<RaisedAlert> =
        sources
            .filter { reader.canActOn(it.audience) }
            .flatMap { it.raised() }
            .sortedWith(compareByDescending<RaisedAlert> { it.since }.thenBy { it.key })

    private fun CurrentUser.canActOn(audience: AlertAudience): Boolean {
        val needed = if (audience == AlertAudience.ADMIN) Role.ADMIN else Role.BOARD
        return roles.any { it.matchesRole(needed) }
    }

    private fun notRaised() = ResponseStatusException(HttpStatus.NOT_FOUND, "No such alert is raised for you")
}
