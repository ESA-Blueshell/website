package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.pinger.api.PingerIdentity
import net.blueshell.api.pinger.api.PingerReportService
import net.blueshell.api.shared.user.MemberIdentities
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The reporting surface the pinger talks to once it is authenticated. A member reaches it with a
 * bearer token and the SiteCie painter with its service token; each report accrues to whichever
 * identity the request resolved to.
 */
@RestController
@RequestMapping("/pinger/report")
@Tag(name = "Pinger report", description = "Authenticated endpoints the pinger clients report to")
class PingerReportController(
    private val reports: PingerReportService,
    private val identities: MemberIdentities,
) {
    // The report chain authorizes every request to a member or SiteCie before it reaches here, so
    // the methods add no gate of their own.
    @PermitAll
    @GetMapping("/whoami")
    fun whoami(authentication: Authentication): WhoAmIResponse {
        val principal = authentication.principal
        return if (principal is Jwt) {
            val subject = principal.subject ?: authentication.name
            WhoAmIResponse(
                subject = subject,
                username = subject.toLongOrNull()?.let { identities.of(listOf(it))[it]?.username },
                member = true,
                roles = principal.getClaimAsStringList("roles").orEmpty(),
            )
        } else {
            WhoAmIResponse(subject = authentication.name, username = null, member = false, roles = emptyList())
        }
    }

    @PermitAll
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun report(
        authentication: Authentication,
        @Valid @RequestBody request: PingerReportRequest,
    ) {
        // `errors` is accepted as part of the report contract but not yet surfaced; this slice
        // accrues the send tally and presence only.
        reports.report(
            identity = PingerIdentity.of(authentication),
            deviceId = request.deviceId,
            online = request.online,
            pps = request.pps,
            sent = request.sent,
        )
    }
}

/** Who the report request resolved to: a member (with their roles) or the SiteCie service. */
@Schema(description = "Who the report request resolved to: a member with their roles, or the SiteCie service")
data class WhoAmIResponse(
    val subject: String,
    @param:Schema(description = "The member's site username, where the subject resolved to one; absent for the SiteCie service")
    val username: String?,
    val member: Boolean,
    val roles: List<String>,
)
