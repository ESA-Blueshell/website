package net.blueshell.api.contribution.domain

import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.enums.MemberType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FeeResolutionTest {

    private val cutoffDate = LocalDate.of(2024, 7, 1)

    private val period = ContributionPeriod(
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 12, 31),
        halfYearCutoffDate = cutoffDate,
        halfYearFee = 50.0,
        fullYearFee = 100.0,
        alumniFee = 30.0,
    )

    private fun feeFor(memberType: MemberType, startDate: LocalDate?): Double? =
        resolveFeeType(memberType, startDate, period)?.let { resolveFeeAmount(it, period) }

    @Nested
    inner class RegularMembers {

        @Test
        fun `starting before the cutoff pays the full year fee`() {
            assertThat(resolveFeeType(MemberType.REGULAR, LocalDate.of(2024, 1, 1), period))
                .isEqualTo(BulkFeeType.FULL_YEAR_FEE)
            assertThat(feeFor(MemberType.REGULAR, LocalDate.of(2024, 1, 1))).isEqualTo(100.0)
        }

        // The boundary is the contract: on the cutoff is a full year, the day after is a half.
        @Test
        fun `starting exactly on the cutoff pays the full year fee`() {
            assertThat(resolveFeeType(MemberType.REGULAR, cutoffDate, period))
                .isEqualTo(BulkFeeType.FULL_YEAR_FEE)
            assertThat(feeFor(MemberType.REGULAR, cutoffDate)).isEqualTo(100.0)
        }

        @Test
        fun `starting the day after the cutoff pays the half year fee`() {
            assertThat(resolveFeeType(MemberType.REGULAR, cutoffDate.plusDays(1), period))
                .isEqualTo(BulkFeeType.HALF_YEAR_FEE)
            assertThat(feeFor(MemberType.REGULAR, cutoffDate.plusDays(1))).isEqualTo(50.0)
        }

        @Test
        fun `starting after the cutoff pays the half year fee`() {
            assertThat(resolveFeeType(MemberType.REGULAR, LocalDate.of(2024, 8, 15), period))
                .isEqualTo(BulkFeeType.HALF_YEAR_FEE)
            assertThat(feeFor(MemberType.REGULAR, LocalDate.of(2024, 8, 15))).isEqualTo(50.0)
        }

        @Test
        fun `an unresolvable start date pays the full year fee`() {
            assertThat(resolveFeeType(MemberType.REGULAR, null, period))
                .isEqualTo(BulkFeeType.FULL_YEAR_FEE)
            assertThat(feeFor(MemberType.REGULAR, null)).isEqualTo(100.0)
        }

        // The cutoff travels with the period, so pricing the same membership against a
        // different period's policy is the only way to get a different answer.
        @Test
        fun `the cutoff comes from the period rather than the caller`() {
            val laterCutoff = ContributionPeriod(
                startDate = period.startDate,
                endDate = period.endDate,
                halfYearCutoffDate = LocalDate.of(2024, 9, 1),
                halfYearFee = 50.0,
                fullYearFee = 100.0,
                alumniFee = 30.0,
            )
            val startedInAugust = LocalDate.of(2024, 8, 15)
            assertThat(resolveFeeType(MemberType.REGULAR, startedInAugust, period))
                .isEqualTo(BulkFeeType.HALF_YEAR_FEE)
            assertThat(resolveFeeType(MemberType.REGULAR, startedInAugust, laterCutoff))
                .isEqualTo(BulkFeeType.FULL_YEAR_FEE)
        }
    }

    @Nested
    inner class AlumniMembers {

        @Test
        fun `pay the alumni fee regardless of start date`() {
            listOf(LocalDate.of(2023, 1, 1), LocalDate.of(2024, 8, 1), null).forEach { startDate ->
                assertThat(resolveFeeType(MemberType.ALUMNI, startDate, period))
                    .isEqualTo(BulkFeeType.ALUMNI_FEE)
                assertThat(feeFor(MemberType.ALUMNI, startDate)).isEqualTo(30.0)
            }
        }
    }

    @Nested
    inner class HonoraryMembers {

        @Test
        fun `are excluded regardless of start date`() {
            listOf(LocalDate.of(2023, 1, 1), LocalDate.of(2024, 8, 1), null).forEach { startDate ->
                assertThat(resolveFeeType(MemberType.HONORARY, startDate, period)).isNull()
            }
        }
    }

    @Nested
    inner class Reasons {

        @Test
        fun `every fee type states a reason`() {
            BulkFeeType.entries.forEach { assertThat(feeReason(it)).isNotBlank() }
        }
    }
}
