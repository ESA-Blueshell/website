package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.contribution.domain.MemberContributions
import net.blueshell.api.contribution.domain.OwnContributions
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@Tag(name = "Contributions")
class MemberContributionController(
    private val contributions: MemberContributions,
    private val currentUser: CurrentUserProvider,
    private val bank: BankProperties,
) {
    /** The reader's own contribution periods, and whether they pay by direct debit. */
    @GetMapping("/users/me/contributions")
    @PreAuthorize("isAuthenticated()")
    fun findOwnContributions(): OwnContributions {
        val reader = currentUser.currentUser()?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        return contributions.of(reader)
    }

    /** The account the payment emails quote, for a member who pays by transfer. */
    @GetMapping("/contributions/bank-account")
    @PreAuthorize("isAuthenticated()")
    fun findBankAccount(): BankAccountResponse = BankAccountResponse(iban = bank.iban, bic = bank.bic, accountName = bank.accountName)
}

@Schema(description = "The association's own bank account, as a member needs it to make a transfer")
data class BankAccountResponse(
    val iban: String,
    val bic: String,
    val accountName: String,
)
