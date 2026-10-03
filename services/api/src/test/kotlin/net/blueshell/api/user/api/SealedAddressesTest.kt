package net.blueshell.api.user.api

import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.domain.sealing.LocalSealer
import net.blueshell.api.user.domain.sealing.Sealer
import net.blueshell.api.user.domain.sealing.SealingUnavailable
import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.persistence.AddressPlaintext
import net.blueshell.api.user.persistence.AddressRepository
import net.blueshell.api.user.persistence.SealedAddressRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper

class SealedAddressesTest {
    private val repository: AddressRepository = mock()
    private val transactions: TransactionTemplate =
        mock {
            on { execute(any<TransactionCallback<Any>>()) } doAnswer
                { (it.arguments[0] as TransactionCallback<*>).doInTransaction(mock()) }
        }
    private val local = LocalSealer()

    private fun addresses(sealer: Sealer = local) =
        SealedAddresses(sealer, repository, JsonMapper.builder().build(), transactions, "api-address")

    private val fields = AddressFields("NL", "Enschede", "Drienerlolaan", "5", "7522NB")

    @Test
    fun `seals an address to its member, empties the plaintext, and opens only for that member`() {
        val ann = Address(user = Entities.user(id = 7))
        addresses().seal(ann, fields)

        assertThat(listOf(ann.country, ann.city, ann.street, ann.houseNumber, ann.zipCode)).containsOnlyNulls()
        assertThat(ann.sealed).doesNotContain("Enschede")
        assertThat(addresses().open(ann)).isEqualTo(fields)

        val copied = Address(user = Entities.user(id = 8)).apply { sealed = ann.sealed }
        assertThat(addresses().open(copied)).isNull()
    }

    @Test
    fun `reads an address the job has not reached in plaintext, and shows none while Vault is away`() {
        val legacy =
            Address(
                user = Entities.user(id = 7),
                country = "NL",
                city = "Enschede",
                street = "Drienerlolaan",
                houseNumber = "5",
                zipCode = "7522NB",
            )
        assertThat(addresses().open(legacy)).isEqualTo(fields)

        val away: Sealer = mock { on { open(any(), any()) } doThrow SealingUnavailable() }
        val sealed = Address(user = Entities.user(id = 7)).apply { sealed = "local:v1:x" }
        assertThat(addresses(away).open(sealed)).isNull()
    }

    @Test
    fun `seals every address left in plaintext, and leaves one no member holds`() {
        val ann = row(1, 7)
        val orphan = row(2, null)
        whenever(repository.findWithPlaintext()).thenReturn(listOf(ann, orphan))
        whenever(repository.writeSealed(eq(1L), any())).thenReturn(1)

        assertThat(addresses().sealEvery()).isEqualTo(1)
        verify(repository, never()).writeSealed(eq(2L), any())
    }

    @Test
    fun `offers every sealed address under its member's context, leaves out one no member holds, and swaps a moved one in`() {
        whenever(repository.findSealed()).thenReturn(listOf(sealedRow(1, 7), sealedRow(2, null)))
        whenever(repository.swapSealed(1, "local:v1:a", "local:v2:a")).thenReturn(1)

        assertThat(addresses().name).isEqualTo("address")
        assertThat(addresses().key).isEqualTo("api-address")
        assertThat(addresses().sealedValues()).containsExactly(SealedValue(1, "local:v1:a", "address:7"))
        assertThat(addresses().swap(1, "local:v1:a", "local:v2:a")).isTrue()
        assertThat(addresses().swap(2, "local:v1:a", "local:v2:a")).isFalse()
    }

    private fun sealedRow(
        id: Long,
        userId: Long?,
    ): SealedAddressRow =
        object : SealedAddressRow {
            override val id = id
            override val userId = userId
            override val sealed = "local:v1:a"
        }

    private fun row(
        id: Long,
        userId: Long?,
    ): AddressPlaintext =
        object : AddressPlaintext {
            override val id = id
            override val userId = userId
            override val country = "NL"
            override val city = "Enschede"
            override val street = "Drienerlolaan"
            override val houseNumber = "5"
            override val zipCode = "7522NB"
        }
}
