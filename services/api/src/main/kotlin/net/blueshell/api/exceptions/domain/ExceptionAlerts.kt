package net.blueshell.api.exceptions.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.exceptions.persistence.RecordedExceptionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Open faults, for an admin; a new occurrence makes a new alert, so hiding one does not hide the next. */
@Component
class ExceptionAlerts(
    private val records: RecordedExceptionRepository,
) : AlertSource {
    override val audience = AlertAudience.ADMIN

    @Transactional(readOnly = true)
    override fun raised(): List<RaisedAlert> {
        val open = records.findAllByResolvedAtIsNull()
        val latest = open.maxOfOrNull { it.lastSeenAt } ?: return emptyList()
        return listOf(
            RaisedAlert(
                key = "exception-open:${latest.toEpochMilli()}",
                kind = AlertKind.EXCEPTION_OPEN,
                subjectId = null,
                subjectLabel = null,
                count = open.size.toLong(),
                since = latest,
            ),
        )
    }
}
