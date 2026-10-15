package net.blueshell.api.user.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.user.api.AddressFields
import net.blueshell.api.user.api.BankDetailsChanged
import net.blueshell.api.user.api.IbanRevealed
import net.blueshell.api.user.api.MandateEnded
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.api.OpenedOnlineMandate
import net.blueshell.api.user.api.OwnMandate
import net.blueshell.api.user.persistence.BankAccount
import net.blueshell.api.user.persistence.IncassoMandate
import net.blueshell.api.user.persistence.MandateKind
import net.blueshell.api.user.persistence.MandateRetentionRepository
import net.blueshell.api.user.persistence.PaymentDetails
import net.blueshell.api.user.persistence.PaymentDetailsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Where a person stands on incasso. */
@Schema(enumAsRef = true)
enum class IncassoStanding {
    /** Pays by transfer: no mandate stands. */
    NONE,

    /** Collected by incasso under a standing mandate. */
    MANDATE_RECORDED,

    /** A mandate stands, but no bank details are on file to collect from. */
    ON_INCASSO_WITHOUT_BANK_DETAILS,
}

/** What a member confirmed when authorising incasso on the site: the wording they saw and their address then. */
data class OnlineAuthorisation(
    val wordingVersion: String,
    val address: AddressFields,
)

/** A person's bank details opened, for the few places the full number may go: a reveal and ING's file. */
data class BankDetails(
    val iban: Iban,
    val accountHolder: String,
) {
    // Only the masked IBAN ever reaches a log line.
    override fun toString(): String = "BankDetails($iban)"
}

/**
 * Whether the mandate stands on [today], which is the only thing that says somebody pays by
 * direct debit (api ADR-040). An end set for a later day, as a membership ending ahead sets, has
 * not come yet. TWIN: `MandateRetentionRepository.STOPPED` reads an end the same way.
 */
fun IncassoMandate.standsOn(today: LocalDate): Boolean = endedOn?.isAfter(today) ?: true

/** The mandate the person can be collected under on [today]: it stands, and the account it was given for is on file. */
fun PaymentDetails.collectableMandate(today: LocalDate): IncassoMandate? = mandate?.takeIf { it.standsOn(today) && mandateAccountKept }

/** A mandate whose account is still on file, standing or ended, for its PDF. */
val PaymentDetails.keptMandate: IncassoMandate?
    get() = mandate?.takeIf { mandateAccountKept }

/** A standing mandate is the only "pays by direct debit" (api ADR-040). */
fun PaymentDetails.incassoStanding(today: LocalDate): IncassoStanding =
    when {
        collectableMandate(today) != null -> IncassoStanding.MANDATE_RECORDED
        mandate?.standsOn(today) == true -> IncassoStanding.ON_INCASSO_WITHOUT_BANK_DETAILS
        else -> IncassoStanding.NONE
    }

/** Records, reads and opens how a person pays and the mandate they are collected under. */
@Service
class Mandates(
    private val payments: PaymentDetailsRepository,
    private val sealing: SealedBankDetails,
    private val clock: Clock,
    // After commit: a change that rolls back is neither logged nor notified.
    private val events: AfterCommitEventPublisher,
    private val retention: MandateRetentionRepository,
) {
    /**
     * Records a paper mandate, replacing any before it, and puts the person on incasso. A
     * mandate the member authorised online is replaced only where the board confirmed that with
     * [replacesOnline], since its record of the authorisation goes with it.
     */
    @Transactional
    fun record(
        userId: Long,
        rawIban: String,
        accountHolder: String,
        signedOn: LocalDate,
        recordedBy: Long?,
        replacesOnline: Boolean = false,
    ): PaymentDetails {
        val iban = Iban.parse(rawIban) ?: throw InvalidIban()
        val holder = accountHolder.trim().ifEmpty { throw AccountHolderMissing() }
        if (signedOn.isAfter(LocalDate.now(clock))) throw MandateSignedInFuture()
        val details = of(userId)
        val online = details.collectableMandate(today())?.takeIf { it.kind == MandateKind.ONLINE }
        if (online != null && !replacesOnline) throw ReplacesOnlineMandate(online.authorisedAt ?: online.recordedAt)
        return write(details, iban, holder, signedOn, recordedBy, null)
    }

    /**
     * Ends the person's standing mandate on [on], today unless said, and tells them: they pay by
     * transfer from then. False where none stood. Bank details stay sealed until the retention wipe.
     */
    @Transactional
    fun end(
        userId: Long,
        on: LocalDate? = null,
    ): Boolean {
        val details = payments.findByUserId(userId) ?: return false
        val mandate = details.mandate?.takeIf { it.standsOn(today()) } ?: return false
        mandate.endedOn = on ?: today()
        payments.save(details)
        events.publish(MandateEnded(userId, mandate.reference))
        return true
    }

    /** Where [details] stands on incasso today. */
    fun standing(details: PaymentDetails): IncassoStanding = details.incassoStanding(today())

    /** Whether [mandate] stands today. */
    fun stands(mandate: IncassoMandate?): Boolean = mandate?.standsOn(today()) == true

    /** A paper mandate kept without bank details, which an incasso run lists as needing them. */
    @Transactional
    fun recordWithoutBankDetails(
        userId: Long,
        recordedBy: Long?,
    ): PaymentDetails {
        val details = of(userId)
        if (details.mandate?.standsOn(today()) == true) return details
        val today = LocalDate.now(clock)
        details.mandate =
            IncassoMandate(
                ibanMasked = null,
                reference = referenceFor(userId, today),
                signedOn = today,
                recordedBy = recordedBy,
                recordedAt = clock.instant(),
            )
        return payments.save(details)
    }

    // A new IBAN gets a new mandate reference; the same IBAN keeps its reference.
    private fun write(
        details: PaymentDetails,
        iban: Iban,
        holder: String,
        signedOn: LocalDate,
        recordedBy: Long?,
        online: OnlineAuthorisation?,
        announce: Boolean = false,
    ): PaymentDetails {
        val userId = details.userId
        // An account that no longer opens for its person is replaced as a new one, under a new reference.
        val onFile =
            details.bankAccount?.let {
                try {
                    bankDetailsOf(userId)
                } catch (_: BankDetailsUnopenable) {
                    null
                }
            }
        // Whoever records it, the person hears that the account they are paid to and collected from changed.
        val replacing = announce || (details.bankAccount != null && onFile?.iban != iban)
        val before = onFile?.takeIf { details.collectableMandate(today()) != null }
        val reference =
            details
                .collectableMandate(today())
                ?.takeIf { before?.iban == iban }
                ?.reference
                ?: referenceFor(userId, signedOn)
        val sealed = sealing.seal(userId, iban, holder, online?.address)
        val now = clock.instant()
        // The same IBAN keeps the bank's BIC; a new one is typed on the bank account page.
        val account = details.replaceAccount(sealed, iban, bic = details.bankAccount?.bic?.takeIf { onFile?.iban == iban }, now)
        details.mandate =
            IncassoMandate(
                ibanMasked = iban.masked,
                reference = reference,
                signedOn = signedOn,
                recordedBy = recordedBy,
                recordedAt = now,
                kind = if (online == null) MandateKind.PAPER else MandateKind.ONLINE,
                authorisedAt = online?.let { now },
                wordingVersion = online?.wordingVersion,
                authorisedBy = online?.let { userId },
                sealedAddress = sealed.address,
                accountVersion = account.version,
            )
        return payments.save(details).also {
            retention.clearMandateCopy(userId)
            if (replacing) events.publish(BankDetailsChanged(userId, requireNotNull(MaskedIban.of(iban.masked))))
        }
    }

    /**
     * A member changing their bank details from their account page, behind a step-up the caller
     * asked for. The security log records it, a first account too, with the new account masked.
     */
    @Transactional
    fun changeOwn(
        userId: Long,
        rawIban: String,
        accountHolder: String,
        authorisation: OnlineAuthorisation,
    ): OwnMandate = setUpOwn(userId, rawIban, accountHolder, authorisation, announce = true)

    /**
     * A member setting up or changing incasso themselves: an online mandate, authorised now under
     * the wording and the address they confirmed. It replaces a paper mandate without asking. One
     * set up during signup is kept on the person and goes unused until their membership runs.
     */
    @Transactional
    fun setUpOwn(
        userId: Long,
        rawIban: String,
        accountHolder: String,
        authorisation: OnlineAuthorisation,
        announce: Boolean = false,
    ): OwnMandate {
        val iban = Iban.parse(rawIban) ?: throw InvalidIban()
        val holder = accountHolder.trim().ifEmpty { throw AccountHolderMissing() }
        if (authorisation.wordingVersion != MandateWording.CURRENT) throw MandateWordingOutdated()
        val address = authorisation.address
        if (listOf(address.country, address.city, address.street, address.houseNumber, address.zipCode).any { it.isNullOrBlank() }) {
            throw MandateAddressMissing()
        }
        write(of(userId), iban, holder, LocalDate.now(clock), userId, authorisation, announce)
        return own(userId)
    }

    /** The person's own mandate and standing, masked. */
    @Transactional(readOnly = true)
    fun own(userId: Long): OwnMandate {
        val details = of(userId)
        val held = details.collectableMandate(today())
        return OwnMandate(details.incassoStanding(today()), MaskedIban.of(held?.ibanMasked), held?.reference, held?.signedOn)
    }

    /** How the person pays; one without a row pays by transfer and has no mandate. */
    @Transactional(readOnly = true)
    fun of(userId: Long): PaymentDetails = payments.findByUserId(userId) ?: PaymentDetails(userId)

    /**
     * The person's full IBAN, for a board member who asked for it. The reveal is written to the
     * person's security log before the IBAN is answered, so none goes out unrecorded.
     */
    @Transactional
    fun reveal(
        userId: Long,
        revealedBy: Long,
    ): Iban {
        // The bank account is opened, mandate or not: a declaration is paid to it too (api ADR-041).
        val details = of(userId)
        val iban = bankDetailsOf(userId).iban
        events.publish(IbanRevealed(userId, details.mandate?.reference, revealedBy))
        return iban
    }

    /** The person's online mandate in full, for its PDF. A paper mandate, a wiped one and none at all have no PDF. */
    @Transactional(readOnly = true)
    fun openOnline(
        userId: Long,
        username: String,
    ): OpenedOnlineMandate {
        val details = of(userId)
        val mandate = details.keptMandate?.takeIf { it.kind == MandateKind.ONLINE } ?: throw NoOnlineMandate()
        val onFile = requireNotNull(details.bankAccount)
        val sealed = SealedAccount(onFile.sealedIban, onFile.sealedAccountHolder, mandate.sealedAddress)
        val (account, address) = sealing.openOnline(userId, sealed)
        val version = requireNotNull(mandate.wordingVersion) { "An online mandate records its wording" }
        return OpenedOnlineMandate(
            userId = userId,
            username = username,
            reference = mandate.reference,
            signedOn = mandate.signedOn,
            authorisedAt = requireNotNull(mandate.authorisedAt) { "An online mandate records when it was authorised" },
            wordingVersion = version,
            wording = requireNotNull(MandateWording.textOf(version)) { "No wording is kept for version $version" },
            accountHolder = account.accountHolder,
            iban = account.iban.value,
            address = address,
        )
    }

    /** The account holder as recorded, or null where it cannot be opened now. Only a reveal carries the IBAN. */
    fun accountHolderOf(userId: Long): String? = of(userId).bankAccount?.let { sealing.accountHolder(held(userId, it)) }

    /** The person's bank account opened, for a reveal, a payout or ING's file. */
    fun bankDetailsOf(userId: Long): BankDetails {
        val account = of(userId).bankAccount ?: throw NoMandateRecorded()
        return sealing.open(listOf(held(userId, account))).single()
    }

    /** The bank accounts of every person, opened in one call, for ING's batch file and nothing else. */
    fun bankDetailsOf(userIds: List<Long>): List<BankDetails> {
        val accounts = payments.findByUserIdIn(userIds.toSet()).associateBy({ it.userId }, { it.bankAccount })
        return sealing.open(userIds.map { userId -> held(userId, accounts[userId] ?: throw NoMandateRecorded()) })
    }

    private fun held(
        userId: Long,
        account: BankAccount,
    ) = HeldAccount(userId, SealedAccount(account.sealedIban, account.sealedAccountHolder))

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun referenceFor(
        userId: Long,
        signedOn: LocalDate,
    ) = "BLUESHELL-$userId-${signedOn.format(DateTimeFormatter.BASIC_ISO_DATE)}"
}
