package net.blueshell.api.user.domain

import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.IbanRevealed
import net.blueshell.api.user.api.SignupMandates
import net.blueshell.api.user.domain.sealing.LocalSealer
import net.blueshell.api.user.domain.sealing.Sealed
import net.blueshell.api.user.domain.sealing.Sealer
import net.blueshell.api.user.domain.sealing.SealingUnavailable
import net.blueshell.api.user.domain.sealing.keyVersionOf
import net.blueshell.api.user.persistence.MemberRepository
import net.blueshell.api.user.persistence.PendingMandate
import net.blueshell.api.user.persistence.PendingMandateRepository
import net.blueshell.api.user.web.MandateController
import net.blueshell.api.user.web.RecordMandateRequest
import net.blueshell.api.user.web.SetUpMandateRequest
import net.blueshell.api.user.web.asResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
    private val repository: MemberRepository = mock()
    private val sealer: Sealer = spy(LocalSealer())
    private val sealing = SealedBankDetails(sealer, "api-bank-details")
    private val pending: PendingMandateRepository = mock()
    private val published: org.springframework.context.ApplicationEventPublisher = mock()
    private val stepUp: net.blueshell.api.security.StepUp = mock()
    private val mandates = Mandates(repository, pending, sealing, Clock.fixed(now, ZoneOffset.UTC), published)
    private val currentUser: CurrentUserProvider = mock()
    private val controller = MandateController(mandates, currentUser, stepUp)
    private val membership =
        Entities.membership(id = 12).also {
            it.createdAt = now
            it.updatedAt = now
        }

    init {
        whenever(repository.findById(12)).thenReturn(Optional.of(membership))
        whenever(repository.findById(13)).thenReturn(Optional.empty())
        whenever(repository.save(any<net.blueshell.api.user.persistence.Membership>())).thenAnswer { it.arguments[0] }
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(3, emptySet(), null))
    }

    @Test
    fun `records a paper mandate masked, puts the membership on incasso, and keeps who recorded it`() {
        assertThat(membership.incassoStanding()).isEqualTo(IncassoStanding.NONE)

        val answer = controller.recordMandate(12, RecordMandateRequest("NL91 ABNA 0417 1643 00", " Ann Vos ", LocalDate.of(2026, 9, 1)))

        assertThat(answer.standing).isEqualTo(IncassoStanding.MANDATE_RECORDED)
        assertThat(listOf(answer.ibanCountry, answer.ibanLastTwo)).containsExactly("NL", "00")
        assertThat(answer.accountHolder).isEqualTo("Ann Vos")
        assertThat(answer.reference).isEqualTo("BLUESHELL-12-20260901")
        assertThat(answer.recordedBy).isEqualTo(3)
        assertThat(answer.recordedAt).isEqualTo(now)
        assertThat(membership.incasso).isTrue()
        assertThat(membership.mandate!!.sealedIban).doesNotContain("0417164300")
        assertThat(mandates.bankDetailsOf(membership.userId, membership.mandate!!).iban.value).isEqualTo("NL91ABNA0417164300")

        val json = JsonMapper.builder().findAndAddModules().build()
        for (response in listOf(answer, controller.findMandate(12), membership.asResponse())) {
            assertThat(json.writeValueAsString(response)).doesNotContain("0417").doesNotContain("NL91ABNA")
        }
        assertThat(json.writeValueAsString(answer)).contains("\"signedOn\":\"2026-09-01\"", "\"membershipId\":12")
    }

    @Test
    fun `keeps the reference for the same IBAN and gives a new IBAN a new one`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann", LocalDate.of(2026, 9, 1), 3)
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 20), 3)
        assertThat(membership.mandate!!.reference).isEqualTo("BLUESHELL-12-20260901")

        mandates.record(12, "GB82 WEST 1234 5698 7654 32", "Ann Vos", LocalDate.of(2026, 9, 21), 3)
        assertThat(membership.mandate!!.reference).isEqualTo("BLUESHELL-12-20260921")
        assertThat(membership.mandate!!.ibanMasked).isEqualTo("GB32")
    }

    @Test
    fun `refuses a wrong IBAN, a missing holder and a signing date after today`() {
        assertThatThrownBy { mandates.record(12, "NL92ABNA0417164300", "Ann", LocalDate.of(2026, 9, 1), 3) }
            .isInstanceOf(InvalidIban::class.java)
        assertThatThrownBy { mandates.record(12, "NL91ABNA0417164300", "  ", LocalDate.of(2026, 9, 1), 3) }
            .isInstanceOf(AccountHolderMissing::class.java)
        assertThatThrownBy { mandates.record(12, "NL91ABNA0417164300", "Ann", LocalDate.of(2026, 10, 1), 3) }
            .isInstanceOf(MandateSignedInFuture::class.java)
        assertThatThrownBy { controller.findMandate(13) }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `a membership on incasso without bank details says so, and nothing of the account reaches a log line`() {
        membership.incasso = true
        assertThat(controller.findMandate(12).standing).isEqualTo(IncassoStanding.ON_INCASSO_WITHOUT_BANK_DETAILS)
        assertThat(controller.findMandate(12).accountHolder).isNull()

        val request = RecordMandateRequest("NL91ABNA0417164300", "Ann", LocalDate.of(2026, 9, 1))
        assertThat(request.toString()).doesNotContain("0417").contains("****4300")
        mandates.record(12, request.iban, request.accountHolder, request.signedOn, null)
        assertThat(membership.mandate.toString()).doesNotContain("0417")
        assertThat(mandates.bankDetailsOf(membership.userId, membership.mandate!!).toString()).doesNotContain("0417")
        val empty = net.blueshell.api.user.persistence.IncassoMandate::class.java.getDeclaredConstructor().newInstance()
        assertThat(empty).isNotNull
        assertThat(PendingMandate::class.java.getDeclaredConstructor().newInstance()).isNotNull
        val waiting = PendingMandate(3, "sealed", "sealed", "NL00", LocalDate.of(2026, 9, 30))
        assertThat(waiting.toString()).isEqualTo("PendingMandate(NL00)")
        assertThat(waiting.userId).isEqualTo(3)
    }

    @Test
    fun `a member sets up incasso themselves, signed today, and a new IBAN gets a new reference`() {
        // The signed-in reader is user 3.
        whenever(repository.findByUser_Id(3)).thenReturn(mutableListOf(membership))

        val own = controller.setUpOwnMandate(SetUpMandateRequest("NL91ABNA0417164300", "Ann Vos", authorised = true))

        assertThat(own.standing).isEqualTo(IncassoStanding.MANDATE_RECORDED)
        assertThat(own.signedOn).isEqualTo(LocalDate.of(2026, 9, 30))
        assertThat(own.pending).isFalse()
        assertThat(membership.mandate!!.recordedBy).isEqualTo(3)
        controller.setUpOwnMandate(SetUpMandateRequest("GB82WEST12345698765432", "Ann Vos", authorised = true))
        assertThat(controller.findOwnMandate().reference).isEqualTo("BLUESHELL-12-20260930")
        assertThat(controller.findOwnMandate().let { listOf(it.ibanCountry, it.ibanLastTwo) }).containsExactly("GB", "32")
        assertThat(SetUpMandateRequest("NL91ABNA0417164300", "Ann").toString()).doesNotContain("0417")
    }

    @Test
    fun `an applicant's details wait until the membership starts, and move onto it then`() {
        whenever(repository.findByUser_Id(membership.userId)).thenReturn(mutableListOf())
        var waiting: PendingMandate? = null
        whenever(pending.save(any<PendingMandate>())).thenAnswer { invocation ->
            (invocation.arguments[0] as PendingMandate).also { waiting = it }
        }
        whenever(pending.findByUserId(membership.userId)).thenAnswer { waiting }

        assertThat(mandates.own(membership.userId).standing).isEqualTo(IncassoStanding.NONE)
        mandates.setUpOwn(membership.userId, "NL91ABNA0417164300", "Ann Vos")
        val again = mandates.setUpOwn(membership.userId, "GB82WEST12345698765432", "Ann Vos")
        assertThat(again.pending).isTrue()
        assertThat(again.iban).isEqualTo(
            net.blueshell.api.user.api
                .MaskedIban("GB", "32"),
        )
        assertThat(again.iban.toString()).isEqualTo("GB•• … ••32")
        assertThat(waiting.toString()).doesNotContain("1234")
        assertThatThrownBy { mandates.setUpOwn(membership.userId, "nope", "Ann") }.isInstanceOf(InvalidIban::class.java)
        assertThatThrownBy { mandates.setUpOwn(membership.userId, "NL91ABNA0417164300", " ") }
            .isInstanceOf(AccountHolderMissing::class.java)

        mandates.adoptPending(membership)

        // The sealed values moved as they were, and still open for the same member.
        assertThat(mandates.bankDetailsOf(membership.userId, membership.mandate!!).iban.value).isEqualTo("GB82WEST12345698765432")
        assertThat(membership.mandate!!.ibanMasked).isEqualTo("GB32")
        assertThat(membership.mandate!!.reference).isEqualTo("BLUESHELL-12-20260930")
        assertThat(membership.incasso).isTrue()
        verify(pending).delete(waiting!!)
        assertThat(PendingMandate::class.java.getDeclaredConstructor().newInstance()).isNotNull
    }

    @Test
    fun `a membership with nothing waiting is left alone, and a running one without details says so`() {
        mandates.adoptPending(membership)
        assertThat(membership.mandate).isNull()

        membership.incasso = true
        whenever(repository.findByUser_Id(membership.userId)).thenReturn(mutableListOf(membership))
        assertThat(mandates.own(membership.userId).standing).isEqualTo(IncassoStanding.ON_INCASSO_WITHOUT_BANK_DETAILS)
        assertThat(SignupMandates(mandates).setUp(membership.userId, "NL91ABNA0417164300", "Ann").standing)
            .isEqualTo(IncassoStanding.MANDATE_RECORDED)
        whenever(currentUser.currentUser()).thenReturn(null)
        assertThatThrownBy { controller.findOwnMandate() }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `a change from the account page asks a step-up, and tells the security log with the account masked`() {
        whenever(repository.findByUser_Id(3)).thenReturn(mutableListOf(membership))
        controller.setUpOwnMandate(SetUpMandateRequest("NL91ABNA0417164300", "Ann Vos", authorised = true))

        org.mockito.kotlin
            .verify(stepUp)
            .require()
        org.mockito.kotlin.verify(published).publishEvent(
            net.blueshell.api.user.api
                .BankDetailsChanged(
                    3,
                    net.blueshell.api.user.api
                        .MaskedIban("NL", "00"),
                ),
        )

        whenever(stepUp.require()).thenThrow(
            net.blueshell.api.security
                .StepUpRequiredException(),
        )
        assertThatThrownBy { controller.setUpOwnMandate(SetUpMandateRequest("NL91ABNA0417164300", "Ann Vos", authorised = true)) }
            .isInstanceOf(net.blueshell.api.security.StepUpRequiredException::class.java)
        org.mockito.kotlin.verifyNoMoreInteractions(published)
    }

    @Test
    fun `seals the IBAN and the account holder in one call, each to its field and the member, on one key version`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        val held = membership.mandate!!

        verify(sealer).seal(
            "api-bank-details",
            listOf(
                Sealed("NL91ABNA0417164300", "iban:${membership.userId}"),
                Sealed("Ann Vos", "account-holder:${membership.userId}"),
            ),
        )
        assertThat(held.sealedAccountHolder).doesNotContain("Ann")
        assertThat(keyVersionOf(held.sealedIban)).isEqualTo(keyVersionOf(held.sealedAccountHolder))
    }

    @Test
    fun `a sealed account does not open for another member, and many open in one call`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        val held = membership.mandate!!

        assertThatThrownBy { mandates.bankDetailsOf(membership.userId + 1, held) }.isInstanceOf(BankDetailsUnopenable::class.java)
        assertThat(controller.findMandate(12).accountHolder).isEqualTo("Ann Vos")

        val opened = mandates.bankDetailsOf(listOf(membership.userId to held, membership.userId to held))
        assertThat(opened.map { it.accountHolder }).containsExactly("Ann Vos", "Ann Vos")
        verify(sealer).open(eq("api-bank-details"), argThat { size == 4 })
    }

    @Test
    fun `with the key out of reach a mandate is refused, and the panel shows no account holder`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        val recorded = membership.mandate!!.sealedIban
        doThrow(SealingUnavailable()).whenever(sealer).seal(any(), any())
        doThrow(SealingUnavailable()).whenever(sealer).open(any(), any())

        assertThatThrownBy { mandates.record(12, "GB82WEST12345698765432", "Ann Vos", LocalDate.of(2026, 9, 2), 3) }
            .isInstanceOf(SealingUnavailable::class.java)
        whenever(repository.findByUser_Id(99)).thenReturn(mutableListOf())
        assertThatThrownBy { mandates.setUpOwn(99, "GB82WEST12345698765432", "Ann Vos") }.isInstanceOf(SealingUnavailable::class.java)
        verify(pending, times(0)).save(any<PendingMandate>())

        assertThat(membership.mandate!!.sealedIban).isEqualTo(recorded)
        val panel = controller.findMandate(12)
        assertThat(panel.accountHolder).isNull()
        assertThat(listOf(panel.ibanCountry, panel.ibanLastTwo)).containsExactly("NL", "00")
    }

    @Test
    fun `a mandate that no longer opens for its member can be recorded again, under a new reference`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        val elsewhere = sealing.seal(membership.userId + 1, requireNotNull(Iban.parse("NL91ABNA0417164300")), "Somebody Else")
        membership.mandate!!.sealedIban = elsewhere.iban
        membership.mandate!!.sealedAccountHolder = elsewhere.accountHolder

        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 20), 3)

        assertThat(membership.mandate!!.reference).isEqualTo("BLUESHELL-12-20260920")
        assertThat(mandates.bankDetailsOf(membership.userId, membership.mandate!!).accountHolder).isEqualTo("Ann Vos")
    }

    @Test
    fun `a board member reveals the full IBAN, sent no-store, and the reveal is published without it`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)

        val answer = controller.revealIban(12)

        assertThat(answer.body!!.iban).isEqualTo("NL91ABNA0417164300")
        assertThat(answer.headers.cacheControl).isEqualTo("no-store")
        assertThat(answer.body.toString()).doesNotContain("0417").contains("****4300")
        val revealed = IbanRevealed(userId = membership.userId, membershipId = 12, revealedBy = 3)
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
}
