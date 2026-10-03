package net.blueshell.api.user.domain

import net.blueshell.api.user.api.SealedValue
import net.blueshell.api.user.domain.sealing.LocalSealer
import net.blueshell.api.user.persistence.PendingMandateRepository
import net.blueshell.api.user.persistence.SealedAccountRow
import net.blueshell.api.user.persistence.SealedMandateRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper

class SealedBankFieldsTest {
    private val memberships: SealedMandateRepository = mock()
    private val pending: PendingMandateRepository = mock()
    private val fields =
        SealedBankFields(memberships, pending, SealedBankDetails(LocalSealer(), JsonMapper.builder().build(), "api-bank-details"))

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
    fun `offers each bank column under its own field and member, and swaps a moved value into that column`() {
        whenever(memberships.findSealedMandates()).thenReturn(listOf(row(12, 7)))
        whenever(pending.findSealed()).thenReturn(listOf(row(4, 9)))
        whenever(memberships.swapSealedIban(12, "a", "b")).thenReturn(1)
        whenever(memberships.swapSealedAccountHolder(12, "a", "b")).thenReturn(0)
        whenever(pending.swapSealedIban(4, "a", "b")).thenReturn(1)
        whenever(pending.swapSealedAccountHolder(4, "a", "b")).thenReturn(1)

        val all =
            listOf(
                fields.mandateIbans(),
                fields.mandateAccountHolders(),
                fields.pendingMandateIbans(),
                fields.pendingMandateAccountHolders(),
            )

        assertThat(all.map { it.name })
            .containsExactly("mandate IBAN", "mandate account holder", "pending mandate IBAN", "pending mandate account holder")
        assertThat(all.map { it.key }).containsOnly("api-bank-details")
        assertThat(all.map { it.sealedValues().single() }).containsExactly(
            SealedValue(12, "local:v1:iban12", "iban:7"),
            SealedValue(12, "local:v1:holder12", "account-holder:7"),
            SealedValue(4, "local:v1:iban4", "iban:9"),
            SealedValue(4, "local:v1:holder4", "account-holder:9"),
        )
        assertThat(all.map { it.swap(if (it.name.startsWith("pending")) 4 else 12, "a", "b") }).containsExactly(true, false, true, true)
    }

    @Test
    fun `offers an online mandate's address, and nothing for a paper mandate, which has none`() {
        whenever(memberships.findSealedMandates()).thenReturn(listOf(row(12, 7)))
        whenever(pending.findSealed()).thenReturn(listOf(row(4, 9)))
        whenever(memberships.swapSealedAddress(12, "a", "b")).thenReturn(1)
        whenever(pending.swapSealedAddress(4, "a", "b")).thenReturn(0)

        assertThat(fields.mandateAddresses().name).isEqualTo("mandate address")
        assertThat(fields.mandateAddresses().sealedValues()).isEmpty()
        assertThat(fields.mandateAddresses().swap(12, "a", "b")).isTrue()
        assertThat(
            fields.pendingMandateAddresses().sealedValues(),
        ).containsExactly(SealedValue(4, "local:v1:address4", "mandate-address:9"))
        assertThat(fields.pendingMandateAddresses().swap(4, "a", "b")).isFalse()
    }
}
