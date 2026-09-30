package net.blueshell.api.user.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.crypto.Sealed
import net.blueshell.api.user.persistence.IncassoMandate
import net.blueshell.api.user.persistence.MemberRepository
import net.blueshell.api.user.persistence.Membership
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Where a membership stands on incasso. */
@Schema(enumAsRef = true)
enum class IncassoStanding {
    /** Pays by transfer. */
    NONE,

    /** Collected by incasso under a recorded mandate. */
    MANDATE_RECORDED,

    /** Marked as paying by incasso, but no bank details are on file to collect from. */
    ON_INCASSO_WITHOUT_BANK_DETAILS,
}

/** A membership's bank details opened, for the one place the full number may go: ING's file. */
data class BankDetails(
    val iban: Iban,
    val accountHolder: String,
) {
    // Only the last four digits ever reach a log line.
    override fun toString(): String = "BankDetails($iban)"
}

fun Membership.incassoStanding(): IncassoStanding =
    when {
        mandate != null -> IncassoStanding.MANDATE_RECORDED
        incasso -> IncassoStanding.ON_INCASSO_WITHOUT_BANK_DETAILS
        else -> IncassoStanding.NONE
    }

/** Records, reads and opens the mandate a membership is collected under. */
@Service
class Mandates(
    private val memberships: MemberRepository,
    private val cipher: BankDetailsCipher,
    private val clock: Clock,
) {
    /**
     * Records the bank details and mandate, replacing any before them, and puts the membership on
     * incasso. A new IBAN gets a new mandate reference; the same IBAN keeps its reference.
     */
    @Transactional
    fun record(
        membershipId: Long,
        rawIban: String,
        accountHolder: String,
        signedOn: LocalDate,
        recordedBy: Long?,
    ): Membership {
        val iban = Iban.parse(rawIban) ?: throw InvalidIban()
        val holder = accountHolder.trim().ifEmpty { throw AccountHolderMissing() }
        if (signedOn.isAfter(LocalDate.now(clock))) throw MandateSignedInFuture()
        val membership = find(membershipId)
        val before = membership.mandate?.let { bankDetailsOf(it) }
        val reference =
            membership.mandate
                ?.takeIf { before?.iban == iban }
                ?.reference
                ?: referenceFor(membershipId, signedOn)
        val sealedIban = cipher.seal(iban.value)
        membership.mandate =
            IncassoMandate(
                keyId = sealedIban.keyId,
                ibanCiphertext = sealedIban.ciphertext,
                accountHolderCiphertext = cipher.seal(holder).ciphertext,
                ibanLastFour = iban.lastFour,
                reference = reference,
                signedOn = signedOn,
                recordedBy = recordedBy,
                recordedAt = clock.instant(),
            )
        membership.incasso = true
        return memberships.save(membership)
    }

    @Transactional(readOnly = true)
    fun find(membershipId: Long): Membership =
        memberships.findById(membershipId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found with id: $membershipId")
        }

    /** The account holder as recorded, which a response may carry; the IBAN it never does. */
    fun accountHolderOf(mandate: IncassoMandate): String = cipher.open(Sealed(mandate.keyId, mandate.accountHolderCiphertext))

    /** The full bank details, for ING's batch file and nothing else. */
    fun bankDetailsOf(mandate: IncassoMandate): BankDetails =
        BankDetails(
            iban = requireNotNull(Iban.parse(cipher.open(Sealed(mandate.keyId, mandate.ibanCiphertext)))),
            accountHolder = accountHolderOf(mandate),
        )

    private fun referenceFor(
        membershipId: Long,
        signedOn: LocalDate,
    ) = "BLUESHELL-$membershipId-${signedOn.format(DateTimeFormatter.BASIC_ISO_DATE)}"
}
