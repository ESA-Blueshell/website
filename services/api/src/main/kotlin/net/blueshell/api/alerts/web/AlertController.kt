package net.blueshell.api.alerts.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.domain.Alerts
import net.blueshell.api.alerts.domain.ReaderAlert
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Schema(name = "Alert")
data class AlertDTO(
    val key: String,
    val kind: AlertKind,
    val subjectId: Long?,
    val subjectLabel: String?,
    val count: Long,
    val since: Instant?,
    val hidden: Boolean,
)

@Schema(name = "AlertKeyRequest")
data class AlertKeyRequest(
    val key: String,
)

@RestController
@RequestMapping("/management/alerts")
@Tag(name = "Alerts", description = "What needs someone in Management")
class AlertController(
    private val alerts: Alerts,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping
    @BoardOnly
    fun listAlerts(): List<AlertDTO> = alerts.forReader(reader()).map { it.toDto() }

    @PostMapping("/hidden")
    @BoardOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun hideAlert(
        @RequestBody request: AlertKeyRequest,
    ) {
        alerts.hide(reader(), request.key)
    }

    @PostMapping("/shown")
    @BoardOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun showAlert(
        @RequestBody request: AlertKeyRequest,
    ) {
        alerts.show(reader(), request.key)
    }

    private fun reader() = currentUser.currentUser() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

    private fun ReaderAlert.toDto() =
        AlertDTO(
            key = alert.key,
            kind = alert.kind,
            subjectId = alert.subjectId,
            subjectLabel = alert.subjectLabel,
            count = alert.count,
            since = alert.since,
            hidden = hidden,
        )
}
