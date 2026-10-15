package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.security.StepUp
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.user.domain.BankAccountPreview
import net.blueshell.api.user.domain.BankAccounts
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

private const val BIC_MAX = 11

/** What any response shows of a bank account: the masked IBAN, and when it was put on file. */
@Schema(name = "BankAccountPreviewResponse")
data class BankAccountPreviewResponse(
    @field:Schema(description = "The IBAN's country code; with the last two, all a response carries of it.")
    val ibanCountry: String,
    val ibanLastTwo: String,
    val recordedAt: Instant,
)

/** A bank account typed in whole, which replaces the one on file. */
@Schema(name = "ReplaceBankAccountRequest")
data class ReplaceBankAccountRequest(
    @field:NotBlank
    @field:Size(max = IBAN_MAX)
    val iban: String,
    @field:NotBlank
    @field:Size(max = ACCOUNT_HOLDER_MAX)
    val accountHolder: String,
    // A BIC typed with spaces between its four parts.
    @field:Size(max = BIC_MAX + 3)
    val bic: String? = null,
) {
    // A request is logged on a failure; the account number is not.
    override fun toString(): String = "ReplaceBankAccountRequest(iban=****${iban.takeLast(SHOWN)})"

    private companion object {
        const val SHOWN = 4
    }
}

@RestController
@Tag(name = "Memberships")
class BankAccountController(
    private val accounts: BankAccounts,
    private val currentUser: CurrentUserProvider,
    private val stepUp: StepUp,
) {
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/users/me/bank-account")
    fun findOwnBankAccount(): ResponseEntity<BankAccountPreviewResponse> = answer(accounts.own(reader()))

    /** Replacing the account a member is paid to and collected from asks a step-up, as a change of bank details does. */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/users/me/bank-account")
    fun replaceOwnBankAccount(
        @Valid @RequestBody request: ReplaceBankAccountRequest,
    ): BankAccountPreviewResponse {
        stepUp.require()
        return accounts.replace(reader(), request.iban, request.accountHolder, request.bic).asResponse()
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/users/me/bank-account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteOwnBankAccount() {
        stepUp.require()
        accounts.delete(reader())
    }

    private fun answer(preview: BankAccountPreview?): ResponseEntity<BankAccountPreviewResponse> =
        preview?.let { ResponseEntity.ok(it.asResponse()) } ?: ResponseEntity.noContent().build()

    private fun reader(): Long = currentUser.currentUser()?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
}

private fun BankAccountPreview.asResponse() = BankAccountPreviewResponse(iban.country, iban.lastTwo, recordedAt)
