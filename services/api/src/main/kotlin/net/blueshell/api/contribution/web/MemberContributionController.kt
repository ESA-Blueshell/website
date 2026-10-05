package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.contribution.domain.FirstContribution
import net.blueshell.api.contribution.domain.FirstContributions
import net.blueshell.api.contribution.domain.MemberContributions
import net.blueshell.api.contribution.domain.MemberPeriodContribution
import net.blueshell.api.contribution.domain.PaymentChannels
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@Tag(name = "Contributions")
class MemberContributionController(
    private val contributions: MemberContributions,
    private val firsts: FirstContributions,
    private val currentUser: CurrentUserProvider,
    private val channels: PaymentChannels,
) {
    /** What the reader pays to make their pending membership active; nothing where none is pending. */
    @GetMapping("/users/me/first-contribution")
    @PreAuthorize("isAuthenticated()")
    fun findOwnFirstContribution(): ResponseEntity<FirstContribution> {
        val reader = currentUser.currentUser()?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        return firsts.owedBy(reader)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()
    }

    /** The reader's own contribution periods, newest first, each with whether it is paid. */
    @GetMapping("/users/me/contributions")
    @PreAuthorize("isAuthenticated()")
    fun findOwnContributions(): List<MemberPeriodContribution> {
        val reader = currentUser.currentUser()?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        return contributions.of(reader)
    }

    /** Where a member who pays by hand transfers the contribution to: the account the payment emails quote. */
    @GetMapping("/contributions/bank-account")
    @PreAuthorize("isAuthenticated()")
    fun findBankAccount(): BankAccountResponse =
        BankAccountResponse(iban = channels.bank.iban, bic = channels.bank.bic, accountName = channels.bank.accountName)

    @GetMapping("/users/{userId}/contributions")
    @BoardOnly
    fun findMemberContributions(
        @PathVariable userId: Long,
    ): List<MemberPeriodContribution> = contributions.of(userId)
}

/** The association's own bank account, as a member needs it to make a transfer. */
data class BankAccountResponse(
    val iban: String,
    val bic: String,
    val accountName: String,
)
