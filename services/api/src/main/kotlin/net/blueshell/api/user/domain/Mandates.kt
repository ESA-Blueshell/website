package net.blueshell.api.user.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.user.api.AddressFields
import net.blueshell.api.user.api.BankDetailsChanged
import net.blueshell.api.user.api.IbanRevealed
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.api.OwnMandate
import net.blueshell.api.user.persistence.IncassoMandate
import net.blueshell.api.user.persistence.MandateKind
import net.blueshell.api.user.persistence.MemberRepository
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.PendingMandate
import net.blueshell.api.user.persistence.PendingMandateRepository
import org.springframework.context.ApplicationEventPublisher
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

/** What a member confirmed when authorising incasso on the site: the wording they saw and their address then. */
data class OnlineAuthorisation(
    val wordingVersion: String,
    val address: AddressFields,
)

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
    private val pendingMandates: PendingMandateRepository,
    private val sealing: SealedBankDetails,
    private val clock: Clock,
    private val events: ApplicationEventPublisher,
) {
    /**
     * Records a paper mandate, replacing any before it, and puts the membership on incasso. A
     * mandate the member authorised online is replaced only where the board confirmed that with
     * [replacesOnline], since its record of the authorisation goes with it.
     */
    @Transactional
    fun record(
        membershipId: Long,
        rawIban: String,
        accountHolder: String,
        signedOn: LocalDate,
        recordedBy: Long?,
        replacesOnline: Boolean = false,
    ): Membership {
        val iban = Iban.parse(rawIban) ?: throw InvalidIban()
        val holder = accountHolder.trim().ifEmpty { throw AccountHolderMissing() }
        if (signedOn.isAfter(LocalDate.now(clock))) throw MandateSignedInFuture()
        val membership = find(membershipId)
        val online = membership.mandate?.takeIf { it.kind == MandateKind.ONLINE }
        if (online != null && !replacesOnline) throw ReplacesOnlineMandate(online.authorisedAt ?: online.recordedAt)
        return write(membership, iban, holder, signedOn, recordedBy, null)
    }

    // A new IBAN gets a new mandate reference; the same IBAN keeps its reference.
    private fun write(
        membership: Membership,
        iban: Iban,
        holder: String,
        signedOn: LocalDate,
        recordedBy: Long?,
        online: OnlineAuthorisation?,
    ): Membership {
        val membershipId = requireNotNull(membership.id)
        // A mandate that no longer opens for its member is replaced as a new one, under a new reference.
        val before =
            membership.mandate?.let { held ->
                try {
                    bankDetailsOf(membership.userId, held)
                } catch (_: BankDetailsUnopenable) {
                    null
                }
            }
        val reference =
            membership.mandate
                ?.takeIf { before?.iban == iban }
                ?.reference
                ?: referenceFor(membershipId, signedOn)
        val sealed = sealing.seal(membership.userId, iban, holder, online?.address)
        val now = clock.instant()
        membership.mandate =
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
                authorisedBy = online?.let { membership.userId },
                sealedAddress = sealed.address,
            )
        membership.incasso = true
        return memberships.save(membership)
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
     * the wording and the address they confirmed. It replaces a paper mandate without asking.
     * Before their membership starts it waits as a pending mandate and moves onto it when it does.
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
        val today = LocalDate.now(clock)
        val running = memberships.findByUser_Id(userId).firstOrNull { it.endDate == null }
        if (running != null) {
            write(running, iban, holder, today, userId, authorisation)
            return own(userId)
        }
        val sealed = sealing.seal(userId, iban, holder, address)
        val sealedAddress = requireNotNull(sealed.address)
        val now = clock.instant()
        val pending = pendingMandates.findByUserId(userId)
        val kept =
            pending?.apply {
                sealedIban = sealed.iban
                sealedAccountHolder = sealed.accountHolder
                ibanMasked = iban.masked
                signedOn = today
                authorisedAt = now
                wordingVersion = authorisation.wordingVersion
                this.sealedAddress = sealedAddress
            }
                ?: PendingMandate(
                    userId,
                    sealed.iban,
                    sealed.accountHolder,
                    iban.masked,
                    today,
                    now,
                    authorisation.wordingVersion,
                    sealedAddress,
                )
        pendingMandates.save(kept)
        return own(userId)
    }

    /** The person's own mandate: on their running membership, else waiting, else none. */
    @Transactional(readOnly = true)
    fun own(userId: Long): OwnMandate {
        val running = memberships.findByUser_Id(userId).firstOrNull { it.endDate == null }
        val held = running?.mandate
        if (held !=
            null
        ) {
            return OwnMandate(IncassoStanding.MANDATE_RECORDED, MaskedIban.of(held.ibanMasked), held.reference, held.signedOn, false)
        }
        val pending = pendingMandates.findByUserId(userId)
        if (pending !=
            null
        ) {
            return OwnMandate(IncassoStanding.MANDATE_RECORDED, MaskedIban.of(pending.ibanMasked), null, pending.signedOn, true)
        }
        return OwnMandate(running?.incassoStanding() ?: IncassoStanding.NONE, null, null, null, false)
    }

    /**
     * Moves a waiting mandate onto the membership that just started, which puts it on incasso. The
     * sealed values move as they are: both belong to the same member, so their context holds.
     */
    @Transactional
    fun adoptPending(membership: Membership) {
        val pending = pendingMandates.findByUserId(membership.userId) ?: return
        membership.mandate =
            IncassoMandate(
                sealedIban = pending.sealedIban,
                sealedAccountHolder = pending.sealedAccountHolder,
                ibanMasked = pending.ibanMasked,
                reference = referenceFor(requireNotNull(membership.id), pending.signedOn),
                signedOn = pending.signedOn,
                recordedBy = membership.userId,
                recordedAt = clock.instant(),
                kind = MandateKind.ONLINE,
                authorisedAt = pending.authorisedAt,
                wordingVersion = pending.wordingVersion,
                authorisedBy = pending.userId,
                sealedAddress = pending.sealedAddress,
            )
        membership.incasso = true
        memberships.save(membership)
        pendingMandates.delete(pending)
    }

    @Transactional(readOnly = true)
    fun find(membershipId: Long): Membership =
        memberships.findById(membershipId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found with id: $membershipId")
        }

    /**
     * The membership's full IBAN, for a board member who asked for it. The reveal is written to the
     * member's security log before the IBAN is answered, so none goes out unrecorded.
     */
    @Transactional
    fun reveal(
        membershipId: Long,
        revealedBy: Long,
    ): Iban {
        val membership = find(membershipId)
        val mandate = membership.mandate ?: throw NoMandateRecorded()
        val iban = bankDetailsOf(membership.userId, mandate).iban
        events.publishEvent(IbanRevealed(membership.userId, membershipId, revealedBy))
        return iban
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
    ) = HeldAccount(userId, SealedAccount(mandate.sealedIban, mandate.sealedAccountHolder))

    private fun referenceFor(
        membershipId: Long,
        signedOn: LocalDate,
    ) = "BLUESHELL-$membershipId-${signedOn.format(DateTimeFormatter.BASIC_ISO_DATE)}"
}
