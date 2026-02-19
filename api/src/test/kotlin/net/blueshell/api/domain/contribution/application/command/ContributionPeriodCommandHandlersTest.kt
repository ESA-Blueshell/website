package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.command.CreateContributionPeriodCommand
import net.blueshell.api.domain.contribution.command.DeleteContributionPeriodByIdCommand
import net.blueshell.api.domain.contribution.command.FindContributionPeriodsCommand
import net.blueshell.api.domain.contribution.command.FindCurrentContributionPeriodCommand
import net.blueshell.api.domain.contribution.command.UpdateContributionPeriodCommand
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class ContributionPeriodCommandHandlersTest {

    private val contributionPeriodService = mock<ContributionPeriodService>()

    @Nested
    inner class FindContributionPeriods {

        private val handler = FindContributionPeriodsHandler(contributionPeriodService)

        @Test
        fun `returns mapped contribution periods`() {
            whenever(contributionPeriodService.findAll()).thenReturn(
                mutableListOf(contributionPeriod(1L), contributionPeriod(2L))
            )

            val result = handler.handle(FindContributionPeriodsCommand())

            assertThat(result).hasSize(2)
            assertThat(result.map { it.id }).containsExactly(1L, 2L)
        }
    }

    @Nested
    inner class FindCurrentContributionPeriod {

        private val handler = FindCurrentContributionPeriodHandler(contributionPeriodService)

        @Test
        fun `returns mapped current contribution period`() {
            whenever(contributionPeriodService.findLatest()).thenReturn(contributionPeriod(5L))

            val result = handler.handle(FindCurrentContributionPeriodCommand())

            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo(5L)
        }

        @Test
        fun `returns null when no current contribution period exists`() {
            whenever(contributionPeriodService.findLatest()).thenReturn(null)

            val result = handler.handle(FindCurrentContributionPeriodCommand())

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class CreateContributionPeriod {

        private val handler = CreateContributionPeriodHandler(contributionPeriodService)

        @Test
        fun `creates contribution period from command fields`() {
            val captured = argumentCaptor<ContributionPeriod>()
            whenever(contributionPeriodService.create(captured.capture())).thenReturn(contributionPeriod(7L))
            val startDate = LocalDate.of(2025, 1, 1)
            val endDate = LocalDate.of(2025, 12, 31)

            val result = handler.handle(
                CreateContributionPeriodCommand(
                    startDate = startDate,
                    endDate = endDate,
                    halfYearFee = 10.0,
                    fullYearFee = 20.0,
                    alumniFee = 5.0,
                    listId = 100L
                )
            )

            assertThat(captured.firstValue.startDate).isEqualTo(startDate)
            assertThat(captured.firstValue.endDate).isEqualTo(endDate)
            assertThat(captured.firstValue.halfYearFee).isEqualTo(10.0)
            assertThat(captured.firstValue.fullYearFee).isEqualTo(20.0)
            assertThat(captured.firstValue.alumniFee).isEqualTo(5.0)
            assertThat(captured.firstValue.listId).isEqualTo(100L)
            assertThat(result.id).isEqualTo(7L)
        }
    }

    @Nested
    inner class UpdateContributionPeriod {

        private val handler = UpdateContributionPeriodHandler(contributionPeriodService)

        @Test
        fun `updates contribution period fields and version`() {
            val existing = contributionPeriod(9L).apply { version = 1L }
            whenever(contributionPeriodService.findById(9L)).thenReturn(existing)
            whenever(contributionPeriodService.update(existing)).thenReturn(existing)
            val startDate = LocalDate.of(2026, 1, 1)
            val endDate = LocalDate.of(2026, 12, 31)

            val result = handler.handle(
                UpdateContributionPeriodCommand(
                    id = 9L,
                    startDate = startDate,
                    endDate = endDate,
                    halfYearFee = 11.0,
                    fullYearFee = 22.0,
                    alumniFee = 6.0,
                    listId = 200L,
                    version = 4L
                )
            )

            assertThat(existing.startDate).isEqualTo(startDate)
            assertThat(existing.endDate).isEqualTo(endDate)
            assertThat(existing.halfYearFee).isEqualTo(11.0)
            assertThat(existing.fullYearFee).isEqualTo(22.0)
            assertThat(existing.alumniFee).isEqualTo(6.0)
            assertThat(existing.listId).isEqualTo(200L)
            assertThat(existing.version).isEqualTo(4L)
            assertThat(result.id).isEqualTo(9L)
        }
    }

    @Nested
    inner class DeleteContributionPeriodById {

        private val handler = DeleteContributionPeriodByIdHandler(contributionPeriodService)

        @Test
        fun `deletes contribution period by id`() {
            handler.handle(DeleteContributionPeriodByIdCommand(id = 14L))

            verify(contributionPeriodService).deleteById(eq(14L))
        }
    }

    private fun contributionPeriod(id: Long): ContributionPeriod = ContributionPeriod(
        startDate = LocalDate.of(2025, 1, 1),
        endDate = LocalDate.of(2025, 12, 31),
        halfYearFee = 10.0,
        fullYearFee = 20.0,
        alumniFee = 5.0,
        listId = 99L,
    ).apply {
        setField(this, "id", id)
        setField(this, "createdAt", Instant.parse("2024-01-01T00:00:00Z"))
        setField(this, "updatedAt", Instant.parse("2024-01-01T00:00:00Z"))
    }

    private fun setField(target: Any, name: String, value: Any?) {
        var current: Class<*>? = target::class.java
        while (current != null) {
            try {
                val field = current.getDeclaredField(name)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        error("Field $name not found on ${target::class.java.name}")
    }
}
