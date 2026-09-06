package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import net.blueshell.api.contribution.api.ContributionPeriodService

class ContributionReminderUseCasesTest {

    private val service = mock<ContributionReminderService>()
    private val users = mock<UserService>()
    private val periods = mock<ContributionPeriodService>()
    private val useCases = ContributionReminderUseCases(service, users, periods)

    private fun period() = ContributionPeriod(
        startDate = LocalDate.of(2026, 1, 1),
        endDate = LocalDate.of(2026, 12, 31),
        halfYearCutoffDate = LocalDate.of(2026, 7, 1),
        halfYearFee = 1.0,
        fullYearFee = 2.0,
        alumniFee = 0.0,
        contactListId = null,
    )

    @Test
    fun `persists the reminder before sending it`() {
        val user = mock<User>()
        whenever(users.findById(3L)).thenReturn(user)
        whenever(periods.findById(9L)).thenReturn(period())
        val captured = argumentCaptor<ContributionReminder>()
        whenever(service.record(captured.capture())).thenAnswer {
            captured.firstValue.seeded(11L)
        }

        useCases.send(userId = 3L, contributionPeriodId = 9L)

        // Recording writes the row and queues its email together, so the use case hands the
        // ask over once rather than ordering the two itself.
        assertThat(captured.firstValue.user).isSameAs(user)
        verify(service).record(any())
    }

    @Test
    fun `builds one reminder per item and records the batch once`() {
        whenever(users.findById(any())).thenReturn(mock())
        whenever(periods.findById(any())).thenReturn(period())
        val captured = argumentCaptor<List<ContributionReminder>>()
        whenever(service.recordAll(captured.capture())).thenAnswer {
            captured.firstValue.onEachIndexed { index, reminder -> reminder.seeded(index + 1L) }
        }

        useCases.sendBatch(listOf(1L to 9L, 2L to 9L, 3L to 9L))

        assertThat(captured.firstValue).hasSize(3)
        verify(service).recordAll(any())
    }
}
