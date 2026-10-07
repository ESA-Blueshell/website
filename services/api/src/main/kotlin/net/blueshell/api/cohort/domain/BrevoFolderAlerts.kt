package net.blueshell.api.cohort.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * For the board: Brevo has folders that share a name, which the Brevo page merges. Read from Brevo
 * at most every few minutes, since every look at the alerts asks; Brevo out of reach says nothing.
 */
@Component
class BrevoFolderAlerts(
    private val folders: TargetFolders,
    private val strategies: TargetStrategies,
    private val clock: Clock,
) : AlertSource {
    override val audience = AlertAudience.BOARD

    @Volatile private var read: Pair<Instant, List<String>>? = null

    override fun raised(): List<RaisedAlert> {
        val shared = sharedNames()
        if (shared.isEmpty()) return emptyList()
        return listOf(
            RaisedAlert(
                // Keyed by the names, so hiding one pair does not hide the next.
                key = "brevo-folders-share-name:${shared.joinToString(",") { it.lowercase() }}",
                kind = AlertKind.BREVO_FOLDERS_SHARE_NAME,
                subjectId = null,
                subjectLabel = shared.joinToString(", "),
                count = shared.size.toLong(),
                since = null,
            ),
        )
    }

    private fun sharedNames(): List<String> {
        val now = clock.instant()
        read?.takeIf { Duration.between(it.first, now) < FRESH }?.let { return it.second }
        if (strategies.find(TargetSystem.BREVO) == null) return emptyList()
        // Brevo out of reach is no finding, so nothing is kept and the next look asks again.
        val states = runCatching { folders.states(TargetSystem.BREVO) }.getOrNull()
        val names =
            states
                .orEmpty()
                .groupBy { it.name.lowercase() }
                .values
                .filter { it.size > 1 }
                .map { it.first().name }
                .sorted()
        if (states != null) read = now to names
        return names
    }

    private companion object {
        val FRESH: Duration = Duration.ofMinutes(10)
    }
}
