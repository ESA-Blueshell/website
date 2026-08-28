package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.api.ContributionService

class ContributionUseCasesTest {

    private val service = mock<ContributionService>()
    private val users = mock<UserService>()
    private val periods = mock<ContributionPeriodService>()
    private val useCases = ContributionUseCases(service, users, periods)

    @Test
    fun `resolves both the user and the period before recording`() {
        val user = mock<User>()
        val period = ContributionPeriod(
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 12, 31),
            halfYearFee = 10.0,
            fullYearFee = 20.0,
            alumniFee = 0.0,
            contactListId = null,
        )
        whenever(users.findById(3L)).thenReturn(user)
        whenever(periods.findById(9L)).thenReturn(period)
        val captured = argumentCaptor<Contribution>()
        whenever(service.create(captured.capture())).thenAnswer {
            captured.firstValue.apply { id = Contribution.Id(3L, 9L) }.seededTimestamps()
        }

        useCases.create(userId = 3L, contributionPeriodId = 9L)

        assertThat(captured.firstValue.user).isSameAs(user)
        assertThat(captured.firstValue.contributionPeriod).isSameAs(period)
    }
}
