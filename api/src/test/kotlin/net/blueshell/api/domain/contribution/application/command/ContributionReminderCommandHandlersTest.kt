package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.command.ContributionReminderItem
import net.blueshell.api.domain.contribution.command.FindContributionRemindersCommand
import net.blueshell.api.domain.contribution.command.SendContributionReminderBatchCommand
import net.blueshell.api.domain.contribution.command.SendContributionReminderCommand
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class ContributionReminderCommandHandlersTest {

    private val contributionReminderService = mock<ContributionReminderService>()
    private val userService = mock<UserService>()
    private val contributionPeriodService = mock<ContributionPeriodService>()

    @Nested
    inner class SendContributionReminder {

        private val handler = SendContributionReminderHandler(
            contributionReminderService,
            userService,
            contributionPeriodService
        )

        @Test
        fun `creates and sends contribution reminder`() {
            val user = mock<User>()
            val period = mock<ContributionPeriod>()
            whenever(userService.findById(3L)).thenReturn(user)
            whenever(contributionPeriodService.findById(9L)).thenReturn(period)
            val captured = argumentCaptor<ContributionReminder>()
            val savedReminder = contributionReminder(3L, 9L)
            whenever(contributionReminderService.create(captured.capture())).thenReturn(savedReminder)

            val result = handler.handle(SendContributionReminderCommand(userId = 3L, contributionPeriodId = 9L))

            assertThat(captured.firstValue.user).isSameAs(user)
            assertThat(captured.firstValue.contributionPeriod).isSameAs(period)
            verify(contributionReminderService).sendReminder(savedReminder)
            assertThat(result.userId).isEqualTo(3L)
            assertThat(result.contributionPeriodId).isEqualTo(9L)
        }
    }

    @Nested
    inner class SendContributionReminderBatch {

        private val handler = SendContributionReminderBatchHandler(
            contributionReminderService,
            userService,
            contributionPeriodService
        )

        @Test
        fun `creates and sends contribution reminders for all batch items`() {
            val user1 = mock<User>()
            val user2 = mock<User>()
            val period1 = mock<ContributionPeriod>()
            val period2 = mock<ContributionPeriod>()
            whenever(userService.findById(1L)).thenReturn(user1)
            whenever(userService.findById(2L)).thenReturn(user2)
            whenever(contributionPeriodService.findById(10L)).thenReturn(period1)
            whenever(contributionPeriodService.findById(11L)).thenReturn(period2)
            val captured = argumentCaptor<MutableList<ContributionReminder>>()
            val savedReminders = mutableListOf(contributionReminder(1L, 10L), contributionReminder(2L, 11L))
            whenever(contributionReminderService.createAll(captured.capture())).thenReturn(savedReminders)

            val result = handler.handle(
                SendContributionReminderBatchCommand(
                    items = mutableListOf(
                        ContributionReminderItem(userId = 1L, contributionPeriodId = 10L),
                        ContributionReminderItem(userId = 2L, contributionPeriodId = 11L)
                    )
                )
            )

            assertThat(captured.firstValue).hasSize(2)
            assertThat(captured.firstValue[0].user).isSameAs(user1)
            assertThat(captured.firstValue[0].contributionPeriod).isSameAs(period1)
            assertThat(captured.firstValue[1].user).isSameAs(user2)
            assertThat(captured.firstValue[1].contributionPeriod).isSameAs(period2)
            verify(contributionReminderService).sendReminders(savedReminders)
            assertThat(result).hasSize(2)
            assertThat(result.map { it.userId }).containsExactly(1L, 2L)
        }
    }

    @Nested
    inner class FindContributionReminders {

        private val handler = FindContributionRemindersHandler(contributionReminderService)

        @Test
        fun `returns mapped reminders for contribution period`() {
            whenever(contributionReminderService.findByContributionPeriodId(15L)).thenReturn(
                mutableListOf(contributionReminder(4L, 15L))
            )

            val result = handler.handle(FindContributionRemindersCommand(contributionPeriodId = 15L))

            assertThat(result).hasSize(1)
            assertThat(result.first().userId).isEqualTo(4L)
            assertThat(result.first().contributionPeriodId).isEqualTo(15L)
        }
    }

    private fun contributionReminder(userId: Long, periodId: Long): ContributionReminder = ContributionReminder(
        id = ContributionReminder.Id(userId, periodId),
        user = mock(),
        contributionPeriod = mock(),
    ).apply {
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
