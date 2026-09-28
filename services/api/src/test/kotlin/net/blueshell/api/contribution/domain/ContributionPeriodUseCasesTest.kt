package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.OptimisticLockingFailureException
import java.time.LocalDate

class ContributionPeriodUseCasesTest {
    private val service = mock<ContributionPeriodService>()
    private val useCases = ContributionPeriodUseCases(service)

    private fun period() =
        ContributionPeriod(
            startDate = LocalDate.of(2025, 1, 1),
            endDate = LocalDate.of(2025, 12, 31),
            halfYearCutoffDate = LocalDate.of(2025, 7, 1),
            halfYearFee = 1.0,
            fullYearFee = 2.0,
            alumniFee = 0.0,
            contactListId = null,
        )

    @Test
    fun `builds a period from the given fees and dates`() {
        val captured = argumentCaptor<ContributionPeriod>()
        whenever(service.create(captured.capture())).thenAnswer { captured.firstValue.seeded() }

        useCases.create(
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 12, 31),
            halfYearCutoffDate = LocalDate.of(2026, 7, 1),
            halfYearFee = 12.5,
            fullYearFee = 25.0,
            alumniFee = 0.0,
            contactListId = 4L,
        )

        assertThat(captured.firstValue.halfYearCutoffDate).isEqualTo(LocalDate.of(2026, 7, 1))
        assertThat(captured.firstValue.halfYearFee).isEqualTo(12.5)
        assertThat(captured.firstValue.fullYearFee).isEqualTo(25.0)
        assertThat(captured.firstValue.contactListId).isEqualTo(4L)
    }

    @Test
    fun `applies every editable field on update, keeping the version it was read at`() {
        val existing = period()
        whenever(service.findById(7L)).thenReturn(existing.seeded(7L))
        whenever(service.update(existing)).thenReturn(existing)

        useCases.update(
            id = 7L,
            startDate = LocalDate.of(2027, 2, 1),
            endDate = LocalDate.of(2027, 11, 1),
            halfYearCutoffDate = LocalDate.of(2027, 6, 1),
            halfYearFee = 30.0,
            fullYearFee = 55.0,
            alumniFee = 5.0,
            contactListId = null,
            version = 0L,
        )

        assertThat(existing.startDate).isEqualTo(LocalDate.of(2027, 2, 1))
        assertThat(existing.halfYearCutoffDate).isEqualTo(LocalDate.of(2027, 6, 1))
        assertThat(existing.fullYearFee).isEqualTo(55.0)
        assertThat(existing.alumniFee).isEqualTo(5.0)
        assertThat(existing.contactListId).isNull()
        assertThat(existing.version).isEqualTo(0L)
    }

    @Test
    fun `refuses an edit made against an older version, before touching a field`() {
        val existing = period().apply { version = 2L }
        whenever(service.findById(7L)).thenReturn(existing.seeded(7L))

        assertThatThrownBy {
            useCases.update(
                id = 7L,
                startDate = LocalDate.of(2027, 2, 1),
                endDate = LocalDate.of(2027, 11, 1),
                halfYearCutoffDate = LocalDate.of(2027, 6, 1),
                halfYearFee = 30.0,
                fullYearFee = 55.0,
                alumniFee = 5.0,
                contactListId = null,
                version = 1L,
            )
        }.isInstanceOf(OptimisticLockingFailureException::class.java)
        assertThat(existing.alumniFee).isNotEqualTo(5.0)
        verify(service, never()).update(any())
    }
}
