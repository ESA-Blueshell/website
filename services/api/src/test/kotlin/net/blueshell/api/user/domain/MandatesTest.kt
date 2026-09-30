package net.blueshell.api.user.domain

import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.persistence.MemberRepository
import net.blueshell.api.user.web.MandateController
import net.blueshell.api.user.web.RecordMandateRequest
import net.blueshell.api.user.web.asResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.env.MockEnvironment
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64
import java.util.Optional

class MandatesTest {
    private val now = Instant.parse("2026-09-30T10:00:00Z")
    private val repository: MemberRepository = mock()
    private val cipher = BankDetailsCipher("1", Base64.getEncoder().encodeToString(ByteArray(32) { 7 }), "", MockEnvironment())
    private val mandates = Mandates(repository, cipher, Clock.fixed(now, ZoneOffset.UTC))
    private val currentUser: CurrentUserProvider = mock()
    private val controller = MandateController(mandates, currentUser)
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
        assertThat(answer.ibanLastFour).isEqualTo("4300")
        assertThat(answer.accountHolder).isEqualTo("Ann Vos")
        assertThat(answer.reference).isEqualTo("BLUESHELL-12-20260901")
        assertThat(answer.recordedBy).isEqualTo(3)
        assertThat(answer.recordedAt).isEqualTo(now)
        assertThat(membership.incasso).isTrue()
        assertThat(membership.mandate!!.ibanCiphertext).doesNotContain("0417164300")
        assertThat(mandates.bankDetailsOf(membership.mandate!!).iban.value).isEqualTo("NL91ABNA0417164300")

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
        assertThat(membership.mandate!!.ibanLastFour).isEqualTo("5432")
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
        assertThat(mandates.bankDetailsOf(membership.mandate!!).toString()).doesNotContain("0417")
    }
}
