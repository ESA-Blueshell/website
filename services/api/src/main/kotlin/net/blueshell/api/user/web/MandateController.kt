package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.security.StepUp
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.api.OwnMandate
import net.blueshell.api.user.domain.Mandates
import net.blueshell.api.user.domain.incassoStanding
import net.blueshell.api.user.persistence.Membership
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@Tag(name = "Memberships")
class MandateController(
    private val mandates: Mandates,
    private val currentUser: CurrentUserProvider,
    private val stepUp: StepUp,
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

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/users/me/mandate")
    fun findOwnMandate(): OwnMandateResponse = mandates.own(reader()).asResponse()

    /**
     * A member sets up or changes incasso themselves, the mandate signed today on the site. A sign-in
     * left open must not quietly swap the account a contribution is taken from, so it asks a step-up.
     */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/users/me/mandate")
    fun setUpOwnMandate(
        @Valid @RequestBody request: SetUpMandateRequest,
    ): OwnMandateResponse {
        stepUp.require()
        return mandates.changeOwn(reader(), request.iban, request.accountHolder).asResponse()
    }

    private fun reader(): Long = currentUser.currentUser()?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

    private fun Membership.asMandateResponse(): MandateResponse {
        val held = mandate
        return MandateResponse(
            membershipId = requireNotNull(id),
            standing = incassoStanding(),
            accountHolder = held?.let { mandates.accountHolderOf(userId, it) },
            ibanCountry = MaskedIban.of(held?.ibanMasked)?.country,
            ibanLastTwo = MaskedIban.of(held?.ibanMasked)?.lastTwo,
            reference = held?.reference,
            signedOn = held?.signedOn,
            recordedBy = held?.recordedBy,
            recordedAt = held?.recordedAt,
        )
    }
}

fun OwnMandate.asResponse() = OwnMandateResponse(standing, iban?.country, iban?.lastTwo, reference, signedOn, pending)
