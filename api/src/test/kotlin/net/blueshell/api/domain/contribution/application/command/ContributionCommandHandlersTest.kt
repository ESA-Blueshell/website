package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.command.CreateContributionCommand
import net.blueshell.api.domain.contribution.command.DeleteContributionCommand
import net.blueshell.api.domain.contribution.command.FindContributionsByPeriodIdCommand
import net.blueshell.api.domain.contribution.command.FindContributionsCommand
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class ContributionCommandHandlersTest {

    private val contributionService = mock<ContributionService>()
    private val userService = mock<UserService>()
    private val contributionPeriodService = mock<ContributionPeriodService>()

    @Nested
    inner class CreateContribution {

        private val handler = CreateContributionHandler(contributionService, userService, contributionPeriodService)

        @Test
        fun `creates contribution for user and period`() {
            val user = mock<User>()
            val period = mock<ContributionPeriod>()
            whenever(userService.findById(3L)).thenReturn(user)
            whenever(contributionPeriodService.findById(9L)).thenReturn(period)
            val captured = argumentCaptor<Contribution>()
            whenever(contributionService.create(captured.capture())).thenReturn(contribution(3L, 9L))

            val result = handler.handle(CreateContributionCommand(userId = 3L, contributionPeriodId = 9L))

            assertThat(captured.firstValue.user).isSameAs(user)
            assertThat(captured.firstValue.contributionPeriod).isSameAs(period)
            assertThat(result.userId).isEqualTo(3L)
            assertThat(result.contributionPeriodId).isEqualTo(9L)
        }
    }

    @Nested
    inner class FindContributions {

        private val handler = FindContributionsHandler(contributionService)

        @Test
        fun `returns mapped contributions for period id`() {
            whenever(contributionService.findByContributionPeriodId(7L)).thenReturn(
                mutableListOf(contribution(1L, 7L), contribution(2L, 7L))
            )

            val result = handler.handle(FindContributionsCommand(contributionPeriodId = 7L))

            assertThat(result).hasSize(2)
            assertThat(result.map { it.userId }).containsExactly(1L, 2L)
        }
    }

    @Nested
    inner class DeleteContribution {

        private val handler = DeleteContributionHandler(contributionService)

        @Test
        fun `deletes contribution by composite id`() {
            handler.handle(DeleteContributionCommand(userId = 5L, contributionPeriodId = 11L))

            verify(contributionService).deleteById(eq(Contribution.Id(5L, 11L)))
        }
    }

    @Nested
    inner class FindContributionsByPeriodId {

        private val handler = FindContributionsByPeriodIdHandler(contributionService)

        @Test
        fun `returns mapped contributions for requested period`() {
            whenever(contributionService.findByContributionPeriodId(12L)).thenReturn(
                mutableListOf(contribution(10L, 12L))
            )

            val result = handler.handle(FindContributionsByPeriodIdCommand(periodId = 12L))

            assertThat(result).hasSize(1)
            assertThat(result.first().userId).isEqualTo(10L)
            assertThat(result.first().contributionPeriodId).isEqualTo(12L)
        }
    }

    private fun contribution(userId: Long, periodId: Long): Contribution = Contribution(
        id = Contribution.Id(userId, periodId)
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
