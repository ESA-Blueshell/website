package net.blueshell.api.alerts.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.alerts.persistence.HiddenAlert
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
     * Every alert this reader's roles can act on, newest cause first. A hide outlives its alert by
     * nothing: once the alert clears, the hide goes with it, so the next time it fires it shows.
     */
    @Transactional
    fun forReader(reader: CurrentUser): List<ReaderAlert> {
        val raised = raisedFor(reader)
        val keys = raised.map { it.key }.toSet()
        val hides = hidden.findAllByUserId(reader.id)
        hidden.deleteAll(hides.filter { it.alertKey !in keys })
        val hiddenKeys = hides.map { it.alertKey }.toSet()
        return raised.map { ReaderAlert(it, it.key in hiddenKeys) }
    }

    /** Hides one alert this reader can see, for them only. */
    @Transactional
    fun hide(
        reader: CurrentUser,
        key: String,
    ) {
        if (raisedFor(reader).none { it.key == key }) throw notRaised()
        if (hidden.findByUserIdAndAlertKey(reader.id, key) == null) {
            hidden.save(HiddenAlert(reader.id, key, clock.instant()))
        }
    }

    /** Shows a hidden alert to this reader again. */
    @Transactional
    fun show(
        reader: CurrentUser,
        key: String,
    ) {
        hidden.findByUserIdAndAlertKey(reader.id, key)?.let { hidden.delete(it) }
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
