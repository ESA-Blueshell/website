package net.blueshell.api.user.domain

import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.user.api.AddressFields
import net.blueshell.api.user.api.BankDetailsChanged
import net.blueshell.api.user.api.MandateEnded
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.domain.sealing.LocalSealer
import net.blueshell.api.user.persistence.MandateRetentionRepository
import net.blueshell.api.user.persistence.PaymentDetails
import net.blueshell.api.user.persistence.PaymentDetailsRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BankAccountsTest {
    private val now = Instant.parse("2026-10-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val payments: PaymentDetailsRepository = mock()
    private val held = mutableMapOf<Long, PaymentDetails>()
    private val sealing = SealedBankDetails(LocalSealer(), JsonMapper.builder().build(), "api-bank-details")
    private val published: org.springframework.context.ApplicationEventPublisher = mock()
    private val events = AfterCommitEventPublisher(published)
    private val retention: MandateRetentionRepository = mock()
    private val mandates = Mandates(payments, sealing, clock, events, retention)
    private val accounts = BankAccounts(payments, retention, sealing, clock, events)

    init {
        whenever(payments.findByUserId(any())).thenAnswer { held[it.arguments[0] as Long] }
        whenever(payments.save(any<PaymentDetails>())).thenAnswer { saving ->
            (saving.arguments[0] as PaymentDetails).also { held[it.userId] = it }
        }
    }

    @Test
    fun `a bank account is sealed to its person and shows only its preview`() {
        assertThat(accounts.own(12)).isNull()

        val preview = accounts.replace(12, "NL91 ABNA 0417 1643 00", " Ann Vos ", null)

        assertThat(preview.iban).isEqualTo(MaskedIban("NL", "00"))
        assertThat(accounts.own(12)).isEqualTo(preview)
        assertThat(preview.recordedAt).isEqualTo(now)
        val kept = held.getValue(12).bankAccount!!
        assertThat(kept.sealedIban).doesNotContain("0417164300")
        assertThat(kept.bic).isNull()
        assertThat(mandates.bankDetailsOf(12).iban.value).isEqualTo("NL91ABNA0417164300")
        assertThat(mandates.bankDetailsOf(12).accountHolder).isEqualTo("Ann Vos")
        verify(published, never()).publishEvent(isA<BankDetailsChanged>())
    }

    @Test
    fun `a bank outside the Netherlands names its BIC, and a Dutch one does not`() {
        assertThatThrownBy { accounts.replace(12, "DE89370400440532013000", "Ann Vos", null) }.isInstanceOf(BicMissing::class.java)
        assertThatThrownBy { accounts.replace(12, "DE89370400440532013000", "Ann Vos", "nope") }.isInstanceOf(InvalidBic::class.java)
        assertThatThrownBy { accounts.replace(12, "NL92ABNA0417164300", "Ann Vos", null) }.isInstanceOf(InvalidIban::class.java)
        assertThatThrownBy { accounts.replace(12, "NL91ABNA0417164300", " ", null) }.isInstanceOf(AccountHolderMissing::class.java)

        accounts.replace(12, "DE89370400440532013000", "Ann Vos", "cobade ffxxx")
        assertThat(held.getValue(12).bankAccount!!.bic).isEqualTo("COBADEFFXXX")
        mandates.record(12, "DE89370400440532013000", "Ann Vos", LocalDate.of(2026, 10, 15), 3)
        assertThat(held.getValue(12).bankAccount!!.bic).isEqualTo("COBADEFFXXX")
        accounts.replace(12, "NL91ABNA0417164300", "Ann Vos", "ABNANL2A")
        assertThat(held.getValue(12).bankAccount!!.bic).isNull()
        verify(retention, org.mockito.kotlin.times(3)).clearMandateCopy(12)
    }

    @Test
    fun `replacing the account ends a mandate given for the old one and tells the member, and deleting it does too`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)
        assertThat(held.getValue(12).incassoStanding(LocalDate.of(2026, 10, 15))).isEqualTo(IncassoStanding.MANDATE_RECORDED)

        accounts.replace(12, "GB82 WEST 1234 5698 7654 32", "Ann Vos", "WESTGB2L")

        assertThat(held.getValue(12).mandate!!.endedOn).isEqualTo(LocalDate.of(2026, 10, 15))
        assertThat(held.getValue(12).incassoStanding(LocalDate.of(2026, 10, 15))).isEqualTo(IncassoStanding.NONE)
        verify(published).publishEvent(MandateEnded(12, "BLUESHELL-12-20260901"))
        verify(published).publishEvent(isA<BankDetailsChanged>())

        controllerSetUpOnline()
        accounts.delete(12)

        assertThat(held.getValue(12).bankAccount).isNull()
        assertThat(held.getValue(12).mandate!!.sealedAddress).isNull()
        assertThat(held.getValue(12).mandateAccountGone).isTrue()
        assertThat(held.getValue(12).incassoStanding(LocalDate.of(2026, 10, 15))).isEqualTo(IncassoStanding.NONE)
        assertThatThrownBy { mandates.bankDetailsOf(12) }.isInstanceOf(NoMandateRecorded::class.java)
    }

    private fun controllerSetUpOnline() {
        val address = AddressFields("NL", "Enschede", "Hallenweg", "5", "7522NH")
        mandates.setUpOwn(12, "GB82WEST12345698765432", "Ann Vos", OnlineAuthorisation(MandateWording.CURRENT, address))
    }

    @Test
    fun `a mandate recorded with bank details keeps them on the person's one account`() {
        mandates.record(12, "NL91ABNA0417164300", "Ann Vos", LocalDate.of(2026, 9, 1), 3)

        val details = held.getValue(12)
        assertThat(details.bankAccount!!.version).isEqualTo(details.mandate!!.accountVersion)
        assertThat(accounts.own(12)!!.iban).isEqualTo(MaskedIban("NL", "00"))
    }
}
