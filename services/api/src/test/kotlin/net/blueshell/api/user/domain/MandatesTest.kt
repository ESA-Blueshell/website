package net.blueshell.api.user.domain

import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.AddressFields
import net.blueshell.api.user.api.BankDetailsChanged
import net.blueshell.api.user.api.IbanRevealed
import net.blueshell.api.user.api.MandateEnded
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.api.SealedAddresses
import net.blueshell.api.user.api.SignupMandates
import net.blueshell.api.user.domain.sealing.LocalSealer
import net.blueshell.api.user.domain.sealing.Sealed
import net.blueshell.api.user.domain.sealing.Sealer
import net.blueshell.api.user.domain.sealing.SealingUnavailable
import net.blueshell.api.user.domain.sealing.keyVersionOf
import net.blueshell.api.user.persistence.MandateKind
import net.blueshell.api.user.persistence.PaymentDetails
import net.blueshell.api.user.persistence.PaymentDetailsRepository
import net.blueshell.api.user.persistence.UserRepository
import net.blueshell.api.user.web.MandateController
import net.blueshell.api.user.web.RecordMandateRequest
import net.blueshell.api.user.web.SignOwnMandateRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional

class MandatesTest {
    private val now = Instant.parse("2026-09-30T10:00:00Z")
    private val payments: PaymentDetailsRepository = mock()
    private val held = mutableMapOf<Long, PaymentDetails>()
    private val sealer: Sealer = spy(LocalSealer())
    private val sealing = SealedBankDetails(sealer, JsonMapper.builder().build(), "api-bank-details")
    private val published: org.springframework.context.ApplicationEventPublisher = mock()
    private val stepUp: net.blueshell.api.security.StepUp = mock()
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 9, 30)
    private val people: UserRepository = mock()
    private val addresses: SealedAddresses = mock()
    private val mandates = Mandates(payments, sealing, clock, AfterCommitEventPublisher(published), mock(), people, addresses)
    private val currentUser: CurrentUserProvider = mock()
    private val home = AddressFields("NL", "Enschede", "Hallenweg", "5", "7522NH")
    private val online = OnlineAuthorisation("2026-10", home)
    private val users: net.blueshell.api.user.api.UserService = mock()
    private val controller = MandateController(mandates, currentUser, stepUp, users)
    private val agreed = SignOwnMandateRequest(authorised = true, wordingVersion = "2026-10")
    private val details = PaymentDetails(12)

    init {
        held[12] = details
        whenever(payments.findByUserId(any())).thenAnswer { held[it.arguments[0] as Long] }
        whenever(payments.findByUserIdIn(any())).thenAnswer { asked ->
            (asked.arguments[0] as Collection<*>).mapNotNull { held[it as Long] }
        }
        whenever(payments.save(any<PaymentDetails>())).thenAnswer { saving ->
            (saving.arguments[0] as PaymentDetails).also { held[it.userId] = it }
        }
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(12, emptySet(), null))
        val reader = Entities.user(12)
        val lives = Entities.address(user = reader).also(reader::replaceAddress)
        whenever(people.findById(12)).thenReturn(Optional.of(reader))
        whenever(addresses.open(lives)).thenReturn(home)
    }

    /** Puts a bank account on file for user 12, as the bank account page does. */
    private fun onFile(raw: String = "NL91ABNA0417164300") {
        val iban = Iban.parse(raw)!!
        details.replaceAccount(sealing.seal(12, iban, "Ann Vos"), iban, null, now)
    }

    @Test
    fun `records a paper mandate masked, which puts the person on incasso, and keeps who recorded it`() {
        assertThat(details.incassoStanding(today)).isEqualTo(IncassoStanding.NONE)

        val answer = controller.recordMandate(12, RecordMandateRequest("NL91 ABNA 0417 1643 00", " Ann Vos ", LocalDate.of(2026, 9, 1)))

        assertThat(answer.standing).isEqualTo(IncassoStanding.MANDATE_RECORDED)
        assertThat(listOf(answer.ibanCountry, answer.ibanLastTwo)).containsExactly("NL", "00")
        assertThat(answer.accountHolder).isEqualTo("Ann Vos")
        assertThat(answer.reference).isEqualTo("BLUESHELL-12-20260901")
        assertThat(answer.recordedBy).isEqualTo(12)
        assertThat(answer.recordedAt).isEqualTo(now)
        assertThat(details.mandate!!.standsOn(today)).isTrue()
        assertThat(details.bankAccount!!.sealedIban).doesNotContain("0417164300")
        assertThat(mandates.bankDetailsOf(details.userId).iban.value).isEqualTo("NL91ABNA0417164300")

        val json = JsonMapper.builder().findAndAddModules().build()
        for (response in listOf(answer, controller.findMandate(12))) {
            assertThat(json.writeValueAsString(response)).doesNotContain("0417").doesNotContain("NL91ABNA")
        }
        assertThat(json.writeValueAsString(answer)).contains("\"signedOn\":\"2026-09-01\"", "\"userId\":12", "\"endedOn\":null")
    }

    @Test
    fun `keeps the reference for the same IBAN and gives a new IBAN a new one`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann", LocalDate.of(2026, 9, 1), 3)
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 20), 3)
        assertThat(details.mandate!!.reference).isEqualTo("BLUESHELL-12-20260901")

        mandates.record(12, "GB82 WEST 1234 5698 7654 32", "Ann Vos", LocalDate.of(2026, 9, 21), 3)
        assertThat(details.mandate!!.reference).isEqualTo("BLUESHELL-12-20260921")
        assertThat(details.mandate!!.ibanMasked).isEqualTo("GB32")
    }

    @Test
    fun `refuses a wrong IBAN, a missing holder and a signing date after today`() {
        assertThatThrownBy { mandates.record(12, "NL92ABNA0417164300", "Ann", LocalDate.of(2026, 9, 1), 3) }
            .isInstanceOf(InvalidIban::class.java)
        assertThatThrownBy { mandates.record(12, "NL91ABNA0417164300", "  ", LocalDate.of(2026, 9, 1), 3) }
            .isInstanceOf(AccountHolderMissing::class.java)
        assertThatThrownBy { mandates.record(12, "NL91ABNA0417164300", "Ann", LocalDate.of(2026, 10, 1), 3) }
            .isInstanceOf(MandateSignedInFuture::class.java)
        assertThat(controller.findMandate(13).standing).isEqualTo(IncassoStanding.NONE)
    }

    @Test
    fun `a person on incasso without bank details says so, and nothing of the account reaches a log line`() {
        mandates.recordWithoutBankDetails(12, recordedBy = 3)
        assertThat(controller.findMandate(12).standing).isEqualTo(IncassoStanding.ON_INCASSO_WITHOUT_BANK_DETAILS)
        assertThat(controller.findMandate(12).accountHolder).isNull()

        val request = RecordMandateRequest("NL91ABNA0417164300", "Ann", LocalDate.of(2026, 9, 1))
        assertThat(request.toString()).doesNotContain("0417").contains("****4300")
        mandates.record(12, request.iban, request.accountHolder, request.signedOn, null)
        assertThat(details.mandate.toString()).doesNotContain("0417")
        assertThat(mandates.bankDetailsOf(details.userId).toString()).doesNotContain("0417")
        val empty = net.blueshell.api.user.persistence.IncassoMandate::class.java.getDeclaredConstructor().newInstance()
        assertThat(empty).isNotNull
        assertThat(PaymentDetails::class.java.getDeclaredConstructor().newInstance()).isNotNull
    }

    @Test
    fun `a member sets up direct debit from the bank account on file, signed today, and signing again keeps the reference`() {
        onFile()

        val own = controller.signOwnMandate(agreed)

        assertThat(own.standing).isEqualTo(IncassoStanding.MANDATE_RECORDED)
        assertThat(own.signedOn).isEqualTo(today)
        assertThat(listOf(own.ibanCountry, own.ibanLastTwo)).containsExactly("NL", "00")
        assertThat(details.mandate!!.recordedBy).isEqualTo(12)
        assertThat(details.mandateAccountKept).isTrue()
        // The account is on file already, so signing seals only the address it is authorised under.
        verify(sealer).seal(eq("api-bank-details"), argThat { size == 1 && single().context == "mandate-address:12" })
        mandates.signOwn(12, "2026-10")
        assertThat(controller.findOwnMandate().reference).isEqualTo("BLUESHELL-12-20260930")
    }

    @Test
    fun `direct debit without a bank account on file is refused, and nothing is recorded`() {
        assertThatThrownBy { mandates.signOwn(12, "2026-10") }.isInstanceOf(NoBankAccount::class.java)
        // Every field has a default, so a body that leaves one out is refused by validation rather than parsing.
        assertThat(SignOwnMandateRequest().let { it.authorised to it.wordingVersion }).isEqualTo(false to "")
        // Without an address on the account, the mandate has none to record.
        onFile()
        whenever(people.findById(12)).thenReturn(Optional.of(Entities.user(12)))
        assertThatThrownBy { controller.signOwnMandate(agreed) }.isInstanceOf(MandateAddressMissing::class.java)
        whenever(people.findById(12)).thenReturn(Optional.empty())
        assertThatThrownBy { controller.signOwnMandate(agreed) }.isInstanceOf(MandateAddressMissing::class.java)
        // An address on file that does not open now is an outage, which the member cannot fix.
        val unopenable = Entities.user(12).also { it.replaceAddress(Entities.address(user = it)) }
        whenever(people.findById(12)).thenReturn(Optional.of(unopenable))
        assertThatThrownBy { controller.signOwnMandate(agreed) }.isInstanceOf(SealingUnavailable::class.java)
        assertThat(details.mandate).isNull()
    }

    @Test
    fun `an applicant's online mandate stands on them before any membership, and puts them on incasso`() {
        held.clear()
        assertThat(mandates.own(12).standing).isEqualTo(IncassoStanding.NONE)
        mandates.setUpOwn(12, "NL91ABNA0417164300", "Ann Vos", online)
        val again = mandates.setUpOwn(12, "GB82WEST12345698765432", "Ann Vos", online)

        assertThat(again.standing).isEqualTo(IncassoStanding.MANDATE_RECORDED)
        assertThat(again.iban).isEqualTo(
            net.blueshell.api.user.api
                .MaskedIban("GB", "32"),
        )
        assertThat(again.iban.toString()).isEqualTo("GB•• … ••32")
        assertThat(again.reference).isEqualTo("BLUESHELL-12-20260930")
        val kept = held.getValue(12)
        assertThat(kept.mandate!!.standsOn(today)).isTrue()
        assertThat(kept.mandate!!.let { listOf(it.kind, it.authorisedAt, it.wordingVersion, it.authorisedBy) })
            .containsExactly(MandateKind.ONLINE, now, "2026-10", 12L)
        assertThat(mandates.bankDetailsOf(12).iban.value).isEqualTo("GB82WEST12345698765432")
        assertThatThrownBy { mandates.setUpOwn(12, "nope", "Ann", online) }.isInstanceOf(InvalidIban::class.java)
        assertThatThrownBy { mandates.setUpOwn(12, "NL91ABNA0417164300", " ", online) }
            .isInstanceOf(AccountHolderMissing::class.java)
    }

    @Test
    fun `the signup's step logs and notifies a change of bank details already on file, and not a first one`() {
        val signup = SignupMandates(mandates)
        signup.setUp(12, "NL91ABNA0417164300", "Ann", "2026-10", online.address)
        verify(published, never()).publishEvent(isA<BankDetailsChanged>())

        signup.setUp(12, "GB82WEST12345698765432", "Ann", "2026-10", online.address)

        verify(published).publishEvent(isA<BankDetailsChanged>())
    }

    @Test
    fun `whoever records a mandate over an account on file, the person is told when it names another account`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 2), 3)
        verify(published, never()).publishEvent(isA<BankDetailsChanged>())

        mandates.record(12, "GB82WEST12345698765432", "Ann Vos", LocalDate.of(2026, 9, 3), 3)

        verify(published).publishEvent(BankDetailsChanged(12, MaskedIban("GB", "32")))
    }

    @Test
    fun `a person on incasso without details says so, and the signup's step records one`() {
        mandates.recordWithoutBankDetails(12, recordedBy = null)
        assertThat(mandates.own(12).standing).isEqualTo(IncassoStanding.ON_INCASSO_WITHOUT_BANK_DETAILS)
        assertThat(SignupMandates(mandates).setUp(12, "NL91ABNA0417164300", "Ann", "2026-10", online.address).standing)
            .isEqualTo(IncassoStanding.MANDATE_RECORDED)
        whenever(currentUser.currentUser()).thenReturn(null)
        assertThatThrownBy { controller.findOwnMandate() }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `ending a mandate puts the person on transfer, keeps it as the record and tells them once`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)

        val ended = controller.endMandate(12)

        assertThat(ended.standing).isEqualTo(IncassoStanding.NONE)
        assertThat(ended.endedOn).isEqualTo(LocalDate.now(clock))
        assertThat(ended.reference).isEqualTo("BLUESHELL-12-20260901")
        assertThat(mandates.end(12)).isFalse()
        assertThat(mandates.end(77)).isFalse()
        verify(published).publishEvent(MandateEnded(12, "BLUESHELL-12-20260901"))

        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 25), 3)
        assertThat(details.mandate!!.reference).isEqualTo("BLUESHELL-12-20260925")
        assertThat(controller.endOwnMandate().standing).isEqualTo(IncassoStanding.NONE)
        verify(stepUp).require()
    }

    @Test
    fun `a mandate ending with a membership that ends ahead stands until that day`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)

        mandates.end(12, today.plusMonths(3))

        assertThat(mandates.stands(details.mandate)).isTrue()
        assertThat(details.collectableMandate(today)).isNotNull()
        assertThat(mandates.standing(details)).isEqualTo(IncassoStanding.MANDATE_RECORDED)
        assertThat(details.mandate!!.standsOn(today.plusMonths(3))).isFalse()
        assertThat(mandates.stands(null)).isFalse()
    }

    @Test
    fun `setting up direct debit asks a step-up, and leaves the bank account and the security log alone`() {
        onFile()
        controller.signOwnMandate(agreed)

        verify(stepUp).require()
        verify(published, never()).publishEvent(any<BankDetailsChanged>())

        details.mandate = null
        whenever(stepUp.require()).thenThrow(
            net.blueshell.api.security
                .StepUpRequiredException(),
        )
        assertThatThrownBy {
            controller.signOwnMandate(agreed)
        }.isInstanceOf(net.blueshell.api.security.StepUpRequiredException::class.java)
        assertThat(details.mandate).isNull()
    }

    @Test
    fun `seals the IBAN and the account holder in one call, each to its field and the member, on one key version`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        val held = details.bankAccount!!

        verify(sealer).seal(
            "api-bank-details",
            listOf(
                Sealed("NL91ABNA0417164300", "iban:${details.userId}"),
                Sealed("Ann Vos", "account-holder:${details.userId}"),
            ),
        )
        assertThat(held.sealedAccountHolder).doesNotContain("Ann")
        assertThat(keyVersionOf(held.sealedIban)).isEqualTo(keyVersionOf(held.sealedAccountHolder))
    }

    @Test
    fun `a sealed account does not open for another member, and many open in one call`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        this.held[13] = PaymentDetails(13).apply { bankAccount = details.bankAccount }

        assertThatThrownBy { mandates.bankDetailsOf(13) }.isInstanceOf(BankDetailsUnopenable::class.java)
        assertThat(controller.findMandate(12).accountHolder).isEqualTo("Ann Vos")

        val opened = mandates.bankDetailsOf(listOf(details.userId, details.userId))
        assertThat(opened.map { it.accountHolder }).containsExactly("Ann Vos", "Ann Vos")
        verify(sealer).open(eq("api-bank-details"), argThat { size == 4 })
    }

    @Test
    fun `with the key out of reach a mandate is refused, and the panel shows no account holder`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        val recorded = details.bankAccount!!.sealedIban
        doThrow(SealingUnavailable()).whenever(sealer).seal(any(), any())
        doThrow(SealingUnavailable()).whenever(sealer).open(any(), any())

        assertThatThrownBy { mandates.record(12, "GB82WEST12345698765432", "Ann Vos", LocalDate.of(2026, 9, 2), 3) }
            .isInstanceOf(SealingUnavailable::class.java)
        assertThatThrownBy {
            mandates.setUpOwn(
                99,
                "GB82WEST12345698765432",
                "Ann Vos",
                online,
            )
        }.isInstanceOf(SealingUnavailable::class.java)
        assertThat(held).doesNotContainKey(99)

        assertThat(details.bankAccount!!.sealedIban).isEqualTo(recorded)
        val panel = controller.findMandate(12)
        assertThat(panel.accountHolder).isNull()
        assertThat(listOf(panel.ibanCountry, panel.ibanLastTwo)).containsExactly("NL", "00")
    }

    @Test
    fun `a mandate that no longer opens for its member can be recorded again, under a new reference`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        val elsewhere = sealing.seal(details.userId + 1, requireNotNull(Iban.parse("NL91ABNA0417164300")), "Somebody Else")
        details.bankAccount!!.sealedIban = elsewhere.iban
        details.bankAccount!!.sealedAccountHolder = elsewhere.accountHolder

        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 20), 3)

        assertThat(details.mandate!!.reference).isEqualTo("BLUESHELL-12-20260920")
        assertThat(mandates.bankDetailsOf(details.userId).accountHolder).isEqualTo("Ann Vos")
    }

    @Test
    fun `a board member reveals the full IBAN, sent no-store, and the reveal is published without it`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)

        val answer = controller.revealIban(12)

        assertThat(answer.body!!.iban).isEqualTo("NL91ABNA0417164300")
        assertThat(answer.headers.cacheControl).isEqualTo("no-store")
        assertThat(answer.body.toString()).doesNotContain("0417").contains("****4300")
        val revealed = IbanRevealed(userId = 12, reference = "BLUESHELL-12-20260901", revealedBy = 12)
        verify(published).publishEvent(revealed)
        assertThat(revealed.toString()).doesNotContain("NL91")
    }

    @Test
    fun `a reveal is refused where no mandate is recorded or the key is out of reach, and nothing is published`() {
        assertThatThrownBy { controller.revealIban(12) }.isInstanceOf(NoMandateRecorded::class.java)

        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        doThrow(SealingUnavailable()).whenever(sealer).open(any(), any())
        assertThatThrownBy { controller.revealIban(12) }.isInstanceOf(SealingUnavailable::class.java)
        verify(published, times(0)).publishEvent(any<IbanRevealed>())
    }

    @Test
    fun `an online mandate records its kind, the moment, the wording and who authorised it, and seals the address with the account`() {
        mandates.setUpOwn(12, "NL91ABNA0417164300", "Ann Vos", online)
        val held = details.mandate!!

        assertThat(held.kind).isEqualTo(MandateKind.ONLINE)
        assertThat(held.authorisedAt).isEqualTo(now)
        assertThat(held.wordingVersion).isEqualTo(MandateWording.CURRENT)
        assertThat(held.authorisedBy).isEqualTo(details.userId)
        assertThat(held.sealedAddress).isNotNull().doesNotContain("Hallenweg").doesNotContain("Enschede")
        verify(sealer).seal(eq("api-bank-details"), argThat { size == 3 && this[2].context == "mandate-address:${details.userId}" })
        val account = details.bankAccount!!
        assertThat(listOf(account.sealedIban, account.sealedAccountHolder, held.sealedAddress!!).map(::keyVersionOf).distinct()).hasSize(1)
        assertThat(controller.findMandate(12).let { it.kind to it.authorisedAt }).isEqualTo(MandateKind.ONLINE to now)
        assertThat(MandateWording.textOf(held.wordingVersion!!)).startsWith("I authorise ESA Blueshell")
        assertThat(MandateWording.textOf("1999-01")).isNull()
    }

    @Test
    fun `a paper mandate records that it is one, with no authorisation and no address`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        val held = details.mandate!!

        assertThat(held.kind).isEqualTo(MandateKind.PAPER)
        assertThat(listOf(held.authorisedAt, held.wordingVersion, held.authorisedBy, held.sealedAddress)).containsOnlyNulls()
        assertThat(controller.findMandate(12).kind).isEqualTo(MandateKind.PAPER)
    }

    @Test
    fun `an online mandate is refused under another wording than the current one, or without a whole address`() {
        assertThatThrownBy { mandates.setUpOwn(12, "NL91ABNA0417164300", "Ann Vos", online.copy(wordingVersion = "2025-01")) }
            .isInstanceOf(MandateWordingOutdated::class.java)
        assertThatThrownBy {
            mandates.setUpOwn(
                12,
                "NL91ABNA0417164300",
                "Ann Vos",
                online.copy(address = online.address.copy(street = " ")),
            )
        }.isInstanceOf(MandateAddressMissing::class.java)
        assertThatThrownBy {
            mandates.setUpOwn(
                12,
                "NL91ABNA0417164300",
                "Ann Vos",
                online.copy(address = online.address.copy(zipCode = null)),
            )
        }.isInstanceOf(MandateAddressMissing::class.java)
        onFile()
        assertThatThrownBy { mandates.signOwn(12, "2025-01") }
            .isInstanceOf(MandateWordingOutdated::class.java)
        whenever(addresses.open(any())).thenReturn(home.copy(city = null))
        assertThatThrownBy { mandates.signOwn(12, "2026-10") }.isInstanceOf(MandateAddressMissing::class.java)
        assertThat(details.mandate).isNull()
    }

    @Test
    fun `a paper mandate replaces an online one only once the board confirmed it, and an online one replaces a paper one without asking`() {
        mandates.setUpOwn(details.userId, "NL91ABNA0417164300", "Ann Vos", online)

        assertThatThrownBy { mandates.record(12, "GB82WEST12345698765432", "Ann Vos", LocalDate.of(2026, 9, 20), 3) }
            .isInstanceOf(ReplacesOnlineMandate::class.java)
            .satisfies({ assertThat((it as ReplacesOnlineMandate).facts).containsEntry("authorisedAt", now.toString()) })
        assertThat(details.mandate!!.kind).isEqualTo(MandateKind.ONLINE)

        val request = RecordMandateRequest("GB82WEST12345698765432", "Ann Vos", LocalDate.of(2026, 9, 20), replacesOnline = true)
        assertThat(controller.recordMandate(12, request).kind).isEqualTo(MandateKind.PAPER)
        assertThat(details.mandate!!.sealedAddress).isNull()

        mandates.setUpOwn(details.userId, "NL91ABNA0417164300", "Ann Vos", online)
        assertThat(details.mandate!!.kind).isEqualTo(MandateKind.ONLINE)
    }

    @Test
    fun `a wiped mandate is a record only, with nothing to collect from or reveal, and recording anew starts a new one`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        val held = details.mandate!!
        details.bankAccount = null
        held.endedOn = LocalDate.of(2026, 9, 2)

        assertThat(details.mandateAccountKept).isFalse()
        assertThat(details.collectableMandate(today)).isNull()
        assertThat(details.incassoStanding(today)).isEqualTo(IncassoStanding.NONE)
        assertThat(mandates.own(details.userId).standing).isEqualTo(IncassoStanding.NONE)
        assertThatThrownBy { controller.revealIban(12) }.isInstanceOf(NoMandateRecorded::class.java)
        assertThatThrownBy { mandates.bankDetailsOf(details.userId) }.isInstanceOf(NoMandateRecorded::class.java)

        val panel = controller.findMandate(12)
        assertThat(panel.bankDetailsWiped).isTrue()
        assertThat(panel.accountHolder).isNull()
        assertThat(listOf(panel.reference, panel.ibanCountry, panel.ibanLastTwo)).containsExactly("BLUESHELL-12-20260901", "NL", "00")

        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 20), 3)
        assertThat(details.mandate!!.reference).isEqualTo("BLUESHELL-12-20260920")
        assertThat(controller.findMandate(12).bankDetailsWiped).isFalse()
    }

    @Test
    fun `opens an online mandate in full in one call, for its PDF, and nothing for a paper or a wiped one`() {
        whenever(users.findById(12)).thenReturn(Entities.user(id = 12, username = "annvos"))
        assertThatThrownBy { mandates.openOnline(12, "annvos") }.isInstanceOf(NoOnlineMandate::class.java)
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        assertThatThrownBy { mandates.openOnline(12, "annvos") }.isInstanceOf(NoOnlineMandate::class.java)

        mandates.setUpOwn(details.userId, "NL91ABNA0417164300", "Ann Vos", online)
        val opened =
            net.blueshell.api.user.api
                .OnlineMandates(mandates, users)
                .open(12)

        assertThat(opened.iban).isEqualTo("NL91ABNA0417164300")
        assertThat(opened.accountHolder).isEqualTo("Ann Vos")
        assertThat(opened.address).isEqualTo(online.address)
        assertThat(opened.wordingVersion to opened.wording).isEqualTo("2026-10" to MandateWording.textOf("2026-10"))
        assertThat(opened.authorisedAt).isEqualTo(now)
        assertThat(opened.username).isEqualTo("annvos")
        assertThat(opened.reference).isEqualTo(details.mandate!!.reference)
        verify(sealer).open(eq("api-bank-details"), argThat { size == 3 })

        // Sealed to another member, it opens for nobody.
        val elsewhere =
            sealing.seal(
                details.userId + 1,
                requireNotNull(Iban.parse("NL91ABNA0417164300")),
                "Somebody Else",
                online.address,
            )
        assertThatThrownBy { sealing.openOnline(details.userId, elsewhere) }.isInstanceOf(BankDetailsUnopenable::class.java)
        val swapped = sealing.seal(details.userId, requireNotNull(Iban.parse("NL91ABNA0417164300")), "Ann Vos", online.address)
        assertThatThrownBy { sealing.openOnline(details.userId, swapped.copy(iban = swapped.accountHolder)) }
            .isInstanceOf(BankDetailsUnopenable::class.java)

        details.bankAccount = null
        assertThatThrownBy { mandates.openOnline(12, "annvos") }.isInstanceOf(NoOnlineMandate::class.java)
    }

    @Test
    fun `the board reads somebody's mandate as they would see it, and who recorded a paper one by name`() {
        assertThat(controller.findMandate(77).standing).isEqualTo(IncassoStanding.NONE)

        whenever(users.findById(12)).thenReturn(Entities.user(id = 3, firstName = "Bo", lastName = "Ard"))
        assertThat(
            controller.recordMandate(12, RecordMandateRequest("NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1))).recordedByName,
        ).isEqualTo("Bo Ard")
        whenever(users.findById(12)).thenThrow(IllegalStateException("gone"))
        assertThat(controller.findMandate(12).recordedByName).isNull()
    }
}
