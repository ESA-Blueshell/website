package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionRepository
import net.blueshell.api.contribution.web.PeriodStandingController
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.MembershipService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class PeriodStandingsTest {
    private val periods: ContributionPeriodService = mock()
    private val memberships: MembershipService = mock()
    private val contributions: ContributionRepository = mock()
    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val controller = PeriodStandingController(PeriodStandings(periods, memberships, contributions, clock))

    @Test
    fun `counts today's members, who paid, who is still to, and whose first contribution is pending`() {
        val period = Entities.period(3, startDate = LocalDate.of(2026, 9, 1))
        val paid = Entities.user(id = 1)
        val veteran = Entities.user(id = 2)
        val newcomer = Entities.user(id = 3)
        val lapsed = Entities.user(id = 4)
        whenever(periods.findLatest()).thenReturn(period)
        whenever(memberships.findOverlappingWithMembers(today, today)).thenReturn(
            listOf(
                Entities.membership(user = paid),
                Entities.membership(user = veteran, startDate = LocalDate.of(2026, 9, 1)),
                Entities.membership(user = newcomer, startDate = LocalDate.of(2026, 9, 20)),
            ),
        )
        whenever(contributions.findByIdContributionPeriodId(3)).thenReturn(
            mutableListOf(Entities.contribution(user = paid, period = period), Entities.contribution(user = lapsed, period = period)),
        )
        whenever(memberships.findByUserIds(setOf(2L, 3L))).thenReturn(
            mapOf(
                2L to listOf(Entities.membership(user = veteran, startDate = LocalDate.of(2024, 9, 1))),
                3L to listOf(Entities.membership(user = newcomer, startDate = LocalDate.of(2026, 9, 20))),
            ),
        )

        val standing = controller.findCurrentPeriodStanding().body!!

        assertThat(standing).isEqualTo(
            PeriodStanding(
                periodId = 3,
                startDate = LocalDate.of(2026, 9, 1),
                endDate = LocalDate.of(2027, 8, 31),
                members = 3,
                paid = 2,
                stillToPay = 2,
                pendingFirstContribution = 1,
            ),
        )
        val json = JsonMapper.builder().findAndAddModules().build()
        assertThat(json.writeValueAsString(standing)).contains(
            "\"periodId\":3",
            "\"startDate\":\"2026-09-01\"",
            "\"endDate\":\"2027-08-31\"",
            "\"members\":3",
            "\"paid\":2",
            "\"stillToPay\":2",
            "\"pendingFirstContribution\":1",
        )
    }

    @Test
    fun `answers nothing before any period exists`() {
        assertThat(controller.findCurrentPeriodStanding().statusCode).isEqualTo(HttpStatus.NO_CONTENT)
    }
}
