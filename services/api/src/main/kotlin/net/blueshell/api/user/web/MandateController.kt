package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.user.domain.Mandates
import net.blueshell.api.user.domain.incassoStanding
import net.blueshell.api.user.persistence.Membership
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Memberships")
class MandateController(
    private val mandates: Mandates,
    private val currentUser: CurrentUserProvider,
) {
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'read')")
    @GetMapping("/memberships/{membershipId}/mandate")
    fun findMandate(
        @PathVariable membershipId: Long,
    ): MandateResponse = mandates.find(membershipId).asMandateResponse()

    /** Records a paper mandate, or replaces the one before it. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'write')")
    @PutMapping("/memberships/{membershipId}/mandate")
    fun recordMandate(
        @PathVariable membershipId: Long,
        @Valid @RequestBody request: RecordMandateRequest,
    ): MandateResponse =
        mandates
            .record(membershipId, request.iban, request.accountHolder, request.signedOn, currentUser.currentUser()?.id)
            .asMandateResponse()

    private fun Membership.asMandateResponse(): MandateResponse {
        val held = mandate
        return MandateResponse(
            membershipId = requireNotNull(id),
            standing = incassoStanding(),
            accountHolder = held?.let { mandates.accountHolderOf(it) },
            ibanLastFour = held?.ibanLastFour,
            reference = held?.reference,
            signedOn = held?.signedOn,
            recordedBy = held?.recordedBy,
            recordedAt = held?.recordedAt,
        )
    }
}
