package net.blueshell.api.user.api

import net.blueshell.api.user.domain.Mandates
import net.blueshell.api.user.persistence.IncassoMandate
import net.blueshell.api.user.persistence.PaymentDetails
import net.blueshell.api.user.persistence.PaymentDetailsRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class PaymentDirectoryTest {
    private val repository: PaymentDetailsRepository = mock()
    private val mandates: Mandates = mock()
    private val directory = PaymentDirectory(repository, mandates)

    init {
        // Whether one stands is the mandates' to say; here it stands until it has an end.
        whenever(mandates.stands(anyOrNull())).thenAnswer { asked ->
            (asked.arguments[0] as IncassoMandate?)?.let { it.endedOn == null } ?: false
        }
    }

    private fun mandate() =
        IncassoMandate("sealed", "sealed", "NL00", "BLUESHELL-1-20260901", LocalDate.of(2026, 9, 1), null, Instant.EPOCH)

    @Test
    fun `a standing mandate pays by direct debit, an ended one or none pays by transfer`() {
        val held = PaymentDetails(1).apply { mandate = mandate() }
        whenever(repository.findByUserIdIn(setOf(1L, 2L))).thenReturn(listOf(held))

        val paying = directory.of(listOf(1L, 2L))

        assertThat(paying.getValue(1).incasso).isTrue()
        assertThat(paying.getValue(1).collectable?.reference).isEqualTo("BLUESHELL-1-20260901")
        assertThat(paying.getValue(2)).isEqualTo(PersonPayment(incasso = false, mandate = null))
        held.mandate!!.sealedIban = null
        assertThat(directory.of(listOf(1L, 2L)).getValue(1).collectable).isNull()
        assertThat(directory.of(listOf(1L, 2L)).getValue(1).incasso).isTrue()
        held.mandate!!.endedOn = LocalDate.of(2026, 10, 1)
        assertThat(directory.of(listOf(1L, 2L)).getValue(1).incasso).isFalse()
    }

    @Test
    fun `asks nothing for nobody, and keeps a paper mandate through the mandates`() {
        assertThat(directory.of(emptyList())).isEmpty()
        verifyNoInteractions(repository)

        directory.payByDirectDebit(3)

        verify(mandates).recordWithoutBankDetails(3, null)
    }
}
