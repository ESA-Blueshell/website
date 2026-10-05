package net.blueshell.api.alerts.api

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/** What an alert is about; the frontend words and links it. */
@Schema(enumAsRef = true)
enum class AlertKind {
    TARGET_DRIFT,
    COHORT_WITHOUT_LIST,
    EMAIL_FAILED,
    JOB_DEAD,
    EXCEPTION_OPEN,
    ROLE_AWAITING_TWO_FACTOR,
    DISCORD_BOT_PERMISSIONS,
}

/** Who can act on an alert. An admin reads every alert; the board, the treasurer included, reads board alerts. */
enum class AlertAudience {
    BOARD,
    ADMIN,
}

/**
 * One thing that needs someone.
 *
 * [key] names the alert for hiding it, and changes when a new cause arrives, so hiding three dead
 * jobs does not hide a fourth. [subjectId] and [subjectLabel] name the one thing it is about, where
 * there is one; [count] is how many it covers.
 */
data class RaisedAlert(
    val key: String,
    val kind: AlertKind,
    val subjectId: Long?,
    val subjectLabel: String?,
    val count: Long,
    val since: Instant?,
)

/** A module's alerts, raised afresh on every read so they clear once their cause is gone. */
interface AlertSource {
    val audience: AlertAudience

    fun raised(): List<RaisedAlert>
}
