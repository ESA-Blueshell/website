package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.MembershipService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class FirstContributionsTest {
    private val memberships: MembershipService = mock()
    private val periods: ContributionPeriodService = mock()
    private val firsts = FirstContributions(memberships, periods)
    private val period = Entities.period(4, startDate = LocalDate.of(2026, 9, 1)).apply { fullYearFee = 30.0 }

    @Test
    fun `names the latest period's fee for a pending membership, and nothing for an active one`() {
        whenever(
            memberships.findByUserId(1),
        ).thenReturn(mutableListOf(Entities.membership(startDate = LocalDate.of(2026, 9, 10), activatedOn = null)))
        whenever(memberships.findByUserId(2)).thenReturn(mutableListOf(Entities.membership()))
        whenever(periods.findLatest()).thenReturn(period)

        val owed = firsts.owedBy(1)
        assertThat(owed?.feeType).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
        assertThat(owed?.amount).isEqualTo(30.0)
        assertThat(owed?.periodId).isEqualTo(4)
        assertThat(firsts.owedBy(2)).isNull()

        whenever(periods.findLatest()).thenReturn(null)
        assertThat(firsts.owedBy(1)?.amount).isNull()
        assertThat(firsts.owedBy(1)?.membershipStartDate).isEqualTo(LocalDate.of(2026, 9, 10))
    }
}
