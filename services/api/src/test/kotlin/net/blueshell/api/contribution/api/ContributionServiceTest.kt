package net.blueshell.api.contribution.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionRepository
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.event.TrackedEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDate

class ContributionServiceTest {
    private val repository: ContributionRepository = mockk()
    private val periods: ContributionPeriodService = mockk()
    private val users: UserService = mockk()
    private val trackedEvents: TrackedEventPublisher = mockk(relaxed = true)
    private val service = spyk(ContributionService(repository, periods, users, trackedEvents))

    @Test
    fun `ensurePaid is a no-op when contribution already exists`() {
        every { repository.existsById(Contribution.Id(7L, 12L)) } returns true

        val created = service.ensurePaid(7L, 12L)

        assertThat(created).isFalse()
        verify(exactly = 0) { users.findById(any()) }
        verify(exactly = 0) { periods.findById(any()) }
        verify(exactly = 0) { service.create(any()) }
    }

    @Test
    fun `ensurePaid creates missing contribution row`() {
        val user = mockk<User>()
        val period = ContributionPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"))
        every { repository.existsById(Contribution.Id(7L, 12L)) } returns false
        every { users.findById(7L) } returns user
        every { periods.findById(12L) } returns period
        every { service.create(any()) } answers { firstArg<Contribution>() }

        val created = service.ensurePaid(7L, 12L)

        assertThat(created).isTrue()
        verify {
            service.create(
                match {
                    it.user === user &&
                        it.contributionPeriod === period
                },
            )
        }
    }

    @Test
    fun `ensurePaid treats duplicate create as already paid`() {
        val user = mockk<User>()
        val period = ContributionPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"))
        every { repository.existsById(Contribution.Id(7L, 12L)) } returnsMany listOf(false, true)
        every { users.findById(7L) } returns user
        every { periods.findById(12L) } returns period
        every { service.create(any()) } throws DataIntegrityViolationException("duplicate contribution")

        val created = service.ensurePaid(7L, 12L)

        assertThat(created).isFalse()
        verify(exactly = 1) { service.create(any()) }
    }
}
