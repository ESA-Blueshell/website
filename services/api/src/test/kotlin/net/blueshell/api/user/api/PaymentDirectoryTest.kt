package net.blueshell.api.user.api

import net.blueshell.api.user.domain.Mandates
import net.blueshell.api.user.persistence.IncassoMandate
import net.blueshell.api.user.persistence.PaymentDetails
import net.blueshell.api.user.persistence.PaymentDetailsRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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

    private fun mandate() =
        IncassoMandate("sealed", "sealed", "NL00", "BLUESHELL-1-20260901", LocalDate.of(2026, 9, 1), null, Instant.EPOCH)

    @Test
    fun `reads how each person pays, and somebody with nothing recorded pays by transfer`() {
        val held = PaymentDetails(1, incasso = true).apply { mandate = mandate() }
        whenever(repository.findByUserIdIn(setOf(1L, 2L))).thenReturn(listOf(held))

        val paying = directory.of(listOf(1L, 2L))

        assertThat(paying.getValue(1).incasso).isTrue()
        assertThat(paying.getValue(1).collectable?.reference).isEqualTo("BLUESHELL-1-20260901")
        assertThat(paying.getValue(2)).isEqualTo(PersonPayment(incasso = false, mandate = null))
        held.mandate!!.sealedIban = null
        assertThat(directory.of(listOf(1L, 2L)).getValue(1).collectable).isNull()
    }

    @Test
    fun `asks nothing for nobody, and sets how somebody pays through the mandates`() {
        assertThat(directory.of(emptyList())).isEmpty()
        verifyNoInteractions(repository)

        directory.payBy(3, incasso = true)

        verify(mandates).payBy(3, true)
    }
}
