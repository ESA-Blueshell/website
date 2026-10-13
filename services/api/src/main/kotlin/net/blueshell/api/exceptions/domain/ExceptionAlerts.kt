package net.blueshell.api.exceptions.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.exceptions.persistence.RecordedExceptionRepository
import org.springframework.stereotype.Component

/** Open faults, for an admin; a new occurrence raises the mark, so hiding the alert does not hide the next. */
@Component
class ExceptionAlerts(
    private val records: RecordedExceptionRepository,
) : AlertSource {
    override val audience = AlertAudience.ADMIN

    override fun raised(): List<RaisedAlert> {
        val open = records.openFaults()
        val latest = open.latest ?: return emptyList()
        return listOf(
            RaisedAlert(
                key = "exception-open",
                kind = AlertKind.EXCEPTION_OPEN,
                subjectId = null,
                subjectLabel = null,
                count = open.faults,
                since = open.oldest,
                mark = latest.toEpochMilli(),
            ),
        )
    }
}
