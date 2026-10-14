package net.blueshell.api.user.domain

import net.blueshell.api.user.api.SealedValue
import net.blueshell.api.user.domain.sealing.LocalSealer
import net.blueshell.api.user.persistence.SealedAccountRow
import net.blueshell.api.user.persistence.SealedMandateRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper

class SealedBankFieldsTest {
    private val mandates: SealedMandateRepository = mock()
    private val fields = SealedBankFields(mandates, SealedBankDetails(LocalSealer(), JsonMapper.builder().build(), "api-bank-details"))

    private fun row(
        id: Long,
        userId: Long,
    ) = object : SealedAccountRow {
        override val id = id
        override val userId = userId
        override val iban = "local:v1:iban$id"
        override val accountHolder = "local:v1:holder$id"
        override val address = if (id == 4L) "local:v1:address$id" else null
    }

    @Test
    fun `offers each bank column under its own field and person, and swaps a moved value into that column`() {
        whenever(mandates.findSealedMandates()).thenReturn(listOf(row(12, 7)))
        whenever(mandates.swapSealedIban(12, "a", "b")).thenReturn(1)
        whenever(mandates.swapSealedAccountHolder(12, "a", "b")).thenReturn(0)

        val all = listOf(fields.mandateIbans(), fields.mandateAccountHolders())

        assertThat(all.map { it.name }).containsExactly("mandate IBAN", "mandate account holder")
        assertThat(all.map { it.key }).containsOnly("api-bank-details")
        assertThat(all.map { it.sealedValues().single() }).containsExactly(
            SealedValue(12, "local:v1:iban12", "iban:7"),
            SealedValue(12, "local:v1:holder12", "account-holder:7"),
        )
        assertThat(all.map { it.swap(12, "a", "b") }).containsExactly(true, false)
    }

    @Test
    fun `offers an online mandate's address, and nothing for a paper mandate, which has none`() {
        whenever(mandates.findSealedMandates()).thenReturn(listOf(row(12, 7), row(4, 9)))
        whenever(mandates.swapSealedAddress(4, "a", "b")).thenReturn(0)

        assertThat(fields.mandateAddresses().name).isEqualTo("mandate address")
        assertThat(fields.mandateAddresses().sealedValues()).containsExactly(SealedValue(4, "local:v1:address4", "mandate-address:9"))
        assertThat(fields.mandateAddresses().swap(4, "a", "b")).isFalse()
    }
}
