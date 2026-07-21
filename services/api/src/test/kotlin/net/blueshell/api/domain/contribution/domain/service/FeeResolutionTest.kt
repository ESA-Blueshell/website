package net.blueshell.api.domain.contribution.domain.service

import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.enums.MemberType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FeeResolutionTest {

    private val period = ContributionPeriod(
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 12, 31),
        halfYearFee = 50.0,
        fullYearFee = 100.0,
        alumniFee = 30.0,
    )

    private val cutoffDate = LocalDate.of(2024, 7, 1)

    @Nested
    inner class RegularMembers {

        @Test
        fun `regular member starting before cutoff pays full year fee`() {
            val startDate = LocalDate.of(2024, 1, 1)
            val resolved = resolveMemberFee(MemberType.REGULAR, startDate, cutoffDate, period)
            assertThat(resolved).isEqualTo(100.0)
        }

        @Test
        fun `regular member starting exactly on cutoff pays full year fee (boundary matches the frontend rule)`() {
            val startDate = LocalDate.of(2024, 7, 1)
            val resolved = resolveMemberFee(MemberType.REGULAR, startDate, cutoffDate, period)
            assertThat(resolved).isEqualTo(100.0)
        }

        @Test
        fun `regular member starting after cutoff pays half year fee`() {
            val startDate = LocalDate.of(2024, 8, 15)
            val resolved = resolveMemberFee(MemberType.REGULAR, startDate, cutoffDate, period)
            assertThat(resolved).isEqualTo(50.0)
        }

        @Test
        fun `regular member with null start date pays full year fee`() {
            val resolved = resolveMemberFee(MemberType.REGULAR, null, cutoffDate, period)
            assertThat(resolved).isEqualTo(100.0)
        }
    }

    @Nested
    inner class AlumniMembers {

        @Test
        fun `alumni member pays alumni fee regardless of start date`() {
            val resolved1 = resolveMemberFee(MemberType.ALUMNI, LocalDate.of(2023, 1, 1), cutoffDate, period)
            val resolved2 = resolveMemberFee(MemberType.ALUMNI, LocalDate.of(2024, 8, 1), cutoffDate, period)
            val resolved3 = resolveMemberFee(MemberType.ALUMNI, null, cutoffDate, period)

            assertThat(resolved1).isEqualTo(30.0)
            assertThat(resolved2).isEqualTo(30.0)
            assertThat(resolved3).isEqualTo(30.0)
        }
    }

    @Nested
    inner class HonoraryMembers {

        @Test
        fun `honorary member is excluded regardless of start date`() {
            val resolved1 = resolveMemberFee(MemberType.HONORARY, LocalDate.of(2023, 1, 1), cutoffDate, period)
            val resolved2 = resolveMemberFee(MemberType.HONORARY, LocalDate.of(2024, 8, 1), cutoffDate, period)
            val resolved3 = resolveMemberFee(MemberType.HONORARY, null, cutoffDate, period)

            assertThat(resolved1).isNull()
            assertThat(resolved2).isNull()
            assertThat(resolved3).isNull()
        }
    }
}
