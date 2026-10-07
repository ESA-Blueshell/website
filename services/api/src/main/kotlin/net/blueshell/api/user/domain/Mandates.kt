package net.blueshell.api.user.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.user.api.AddressFields
import net.blueshell.api.user.api.BankDetailsChanged
import net.blueshell.api.user.api.IbanRevealed
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.api.OpenedOnlineMandate
import net.blueshell.api.user.api.OwnMandate
import net.blueshell.api.user.persistence.IncassoMandate
import net.blueshell.api.user.persistence.MandateKind
import net.blueshell.api.user.persistence.PaymentDetails
import net.blueshell.api.user.persistence.PaymentDetailsRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Where a person stands on incasso. */
@Schema(enumAsRef = true)
enum class IncassoStanding {
    /** Pays by transfer. */
    NONE,

    /** Collected by incasso under a recorded mandate. */
    MANDATE_RECORDED,

    /** Marked as paying by incasso, but no bank details are on file to collect from. */
    ON_INCASSO_WITHOUT_BANK_DETAILS,
}

/** What a member confirmed when authorising incasso on the site: the wording they saw and their address then. */
data class OnlineAuthorisation(
    val wordingVersion: String,
    val address: AddressFields,
)

/** A person's bank details opened, for the one place the full number may go: ING's file. */
data class BankDetails(
    val iban: Iban,
    val accountHolder: String,
) {
    // Only the last four digits ever reach a log line.
    override fun toString(): String = "BankDetails($iban)"
}

/** The mandate the person can be collected under: one whose bank details were wiped is a record only. */
val PaymentDetails.collectableMandate: IncassoMandate?
    get() = mandate?.takeUnless { it.wiped }

fun PaymentDetails.incassoStanding(): IncassoStanding =
    when {
        collectableMandate != null -> IncassoStanding.MANDATE_RECORDED
        incasso -> IncassoStanding.ON_INCASSO_WITHOUT_BANK_DETAILS
        else -> IncassoStanding.NONE
    }

/** Records, reads and opens how a person pays and the mandate they are collected under. */
@Service
class Mandates(
    private val payments: PaymentDetailsRepository,
    private val sealing: SealedBankDetails,
    private val clock: Clock,
    private val events: ApplicationEventPublisher,
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
        val online = details.collectableMandate?.takeIf { it.kind == MandateKind.ONLINE }
        if (online != null && !replacesOnline) throw ReplacesOnlineMandate(online.authorisedAt ?: online.recordedAt)
        return write(details, iban, holder, signedOn, recordedBy, null)
    }

    /** Sets whether the person pays by incasso or by transfer; a mandate on file stays either way. */
    @Transactional
    fun payBy(
        userId: Long,
        incasso: Boolean,
    ): PaymentDetails {
        val details = of(userId)
        details.incasso = incasso
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
            details.collectableMandate?.let { held ->
                try {
                    bankDetailsOf(userId, held)
                } catch (_: BankDetailsUnopenable) {
                    null
                }
            }
        val reference =
            details.collectableMandate
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
        details.incasso = true
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
            own.iban?.let { events.publishEvent(BankDetailsChanged(userId, it)) }
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
        val held = details.collectableMandate
        return OwnMandate(details.incassoStanding(), MaskedIban.of(held?.ibanMasked), held?.reference, held?.signedOn)
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
        val mandate = of(userId).collectableMandate ?: throw NoMandateRecorded()
        val iban = bankDetailsOf(userId, mandate).iban
        events.publishEvent(IbanRevealed(userId, mandate.reference, revealedBy))
        return iban
    }

    /** The person's online mandate in full, for its PDF. A paper mandate, a wiped one and none at all have no PDF. */
    @Transactional(readOnly = true)
    fun openOnline(
        userId: Long,
        username: String,
    ): OpenedOnlineMandate {
        val mandate = of(userId).collectableMandate?.takeIf { it.kind == MandateKind.ONLINE } ?: throw NoOnlineMandate()
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

    private fun referenceFor(
        userId: Long,
        signedOn: LocalDate,
    ) = "BLUESHELL-$userId-${signedOn.format(DateTimeFormatter.BASIC_ISO_DATE)}"
}
