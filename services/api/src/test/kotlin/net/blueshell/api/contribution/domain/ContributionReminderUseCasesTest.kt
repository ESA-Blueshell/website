package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
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
        whenever(service.create(captured.capture())).thenAnswer {
            captured.firstValue.apply { id = ContributionReminder.Id(3L, 9L) }.seededTimestamps()
        }

        useCases.send(userId = 3L, contributionPeriodId = 9L)

        assertThat(captured.firstValue.user).isSameAs(user)
        // Order matters: a send failure must leave a record behind.
        inOrder(service).apply {
            verify(service).create(any())
            verify(service).sendReminder(any())
        }
    }

    @Test
    fun `builds one reminder per item and sends the batch once`() {
        whenever(users.findById(any())).thenReturn(mock())
        whenever(periods.findById(any())).thenReturn(period())
        val captured = argumentCaptor<MutableList<ContributionReminder>>()
        whenever(service.createAll(captured.capture())).thenAnswer {
            captured.firstValue.onEach { r ->
                r.id = ContributionReminder.Id(1L, 9L)
                r.seededTimestamps()
            }
        }

        useCases.sendBatch(listOf(1L to 9L, 2L to 9L, 3L to 9L))

        assertThat(captured.firstValue).hasSize(3)
        inOrder(service).apply {
            verify(service).createAll(any())
            verify(service).sendReminders(any())
        }
    }
}
