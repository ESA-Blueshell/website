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
import net.blueshell.api.user.persistence.IncassoMandate
import net.blueshell.api.user.persistence.MandateKind
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

/** The mandate the person can be collected under on [today]: it stands and still holds its bank details. */
fun PaymentDetails.collectableMandate(today: LocalDate): IncassoMandate? = mandate?.takeIf { it.standsOn(today) && !it.wiped }

/** A mandate whose bank details are still kept, standing or ended, for a reveal or its PDF. */
val PaymentDetails.keptMandate: IncassoMandate?
    get() = mandate?.takeUnless { it.wiped }

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

    /** The mandate [userId] can be collected under today, or nothing. */
    @Transactional(readOnly = true)
    fun collectable(userId: Long): IncassoMandate? = payments.findByUserId(userId)?.collectableMandate(today())

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
                sealedIban = null,
                sealedAccountHolder = null,
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
    ): PaymentDetails {
        val userId = details.userId
        // A mandate that no longer opens for its person is replaced as a new one, under a new reference.
        val before =
            details.collectableMandate(today())?.let { held ->
                try {
                    bankDetailsOf(userId, held)
                } catch (_: BankDetailsUnopenable) {
                    null
                }
            }
        val reference =
            details
                .collectableMandate(today())
                ?.takeIf { before?.iban == iban }
                ?.reference
                ?: referenceFor(userId, signedOn)
        val sealed = sealing.seal(userId, iban, holder, online?.address)
        val now = clock.instant()
        details.mandate =
            IncassoMandate(
                sealedIban = sealed.iban,
                sealedAccountHolder = sealed.accountHolder,
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
            )
        return payments.save(details)
    }

    /**
     * A member changing their bank details from their account page, behind a step-up the caller
     * asked for. The security log records it, and a notification names the new account, masked.
     */
    @Transactional
    fun changeOwn(
        userId: Long,
        rawIban: String,
        accountHolder: String,
        authorisation: OnlineAuthorisation,
    ): OwnMandate =
        setUpOwn(userId, rawIban, accountHolder, authorisation).also { own ->
            own.iban?.let { events.publish(BankDetailsChanged(userId, it)) }
        }

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
    ): OwnMandate {
        val iban = Iban.parse(rawIban) ?: throw InvalidIban()
        val holder = accountHolder.trim().ifEmpty { throw AccountHolderMissing() }
        if (authorisation.wordingVersion != MandateWording.CURRENT) throw MandateWordingOutdated()
        val address = authorisation.address
        if (listOf(address.country, address.city, address.street, address.houseNumber, address.zipCode).any { it.isNullOrBlank() }) {
            throw MandateAddressMissing()
        }
        write(of(userId), iban, holder, LocalDate.now(clock), userId, authorisation)
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
        val mandate = of(userId).keptMandate ?: throw NoMandateRecorded()
        val iban = bankDetailsOf(userId, mandate).iban
        events.publish(IbanRevealed(userId, mandate.reference, revealedBy))
        return iban
    }

    /** The person's online mandate in full, for its PDF. A paper mandate, a wiped one and none at all have no PDF. */
    @Transactional(readOnly = true)
    fun openOnline(
        userId: Long,
        username: String,
    ): OpenedOnlineMandate {
        val mandate = of(userId).keptMandate?.takeIf { it.kind == MandateKind.ONLINE } ?: throw NoOnlineMandate()
        val sealed = SealedAccount(requireNotNull(mandate.sealedIban), requireNotNull(mandate.sealedAccountHolder), mandate.sealedAddress)
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
    fun accountHolderOf(
        userId: Long,
        mandate: IncassoMandate,
    ): String? = sealing.accountHolder(held(userId, mandate))

    /** The full bank details of one mandate. */
    fun bankDetailsOf(
        userId: Long,
        mandate: IncassoMandate,
    ): BankDetails = sealing.open(listOf(held(userId, mandate))).single()

    /** The full bank details of every mandate, opened in one call, for ING's batch file and nothing else. */
    fun bankDetailsOf(mandates: List<Pair<Long, IncassoMandate>>): List<BankDetails> =
        sealing.open(mandates.map { (userId, mandate) -> held(userId, mandate) })

    private fun held(
        userId: Long,
        mandate: IncassoMandate,
    ) = HeldAccount(
        userId,
        SealedAccount(
            mandate.sealedIban ?: throw NoMandateRecorded(),
            mandate.sealedAccountHolder ?: throw NoMandateRecorded(),
        ),
    )

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun referenceFor(
        userId: Long,
        signedOn: LocalDate,
    ) = "BLUESHELL-$userId-${signedOn.format(DateTimeFormatter.BASIC_ISO_DATE)}"
}
