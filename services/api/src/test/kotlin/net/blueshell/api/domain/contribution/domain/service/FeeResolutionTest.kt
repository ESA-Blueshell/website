package net.blueshell.api.domain.contribution.domain.service

import net.blueshell.api.shared.dto.bulk.BulkFeeType
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

    private fun feeFor(memberType: MemberType, startDate: LocalDate?): Double? =
        resolveFeeType(memberType, startDate, cutoffDate)?.let { resolveFeeAmount(it, period) }

    @Nested
    inner class RegularMembers {

        @Test
        fun `starting before the cutoff pays the full year fee`() {
            assertThat(resolveFeeType(MemberType.REGULAR, LocalDate.of(2024, 1, 1), cutoffDate))
                .isEqualTo(BulkFeeType.FULL_YEAR_FEE)
            assertThat(feeFor(MemberType.REGULAR, LocalDate.of(2024, 1, 1))).isEqualTo(100.0)
        }

        @Test
        fun `starting exactly on the cutoff pays the full year fee`() {
            assertThat(resolveFeeType(MemberType.REGULAR, cutoffDate, cutoffDate))
                .isEqualTo(BulkFeeType.FULL_YEAR_FEE)
            assertThat(feeFor(MemberType.REGULAR, cutoffDate)).isEqualTo(100.0)
        }

        @Test
        fun `starting after the cutoff pays the half year fee`() {
            assertThat(resolveFeeType(MemberType.REGULAR, LocalDate.of(2024, 8, 15), cutoffDate))
                .isEqualTo(BulkFeeType.HALF_YEAR_FEE)
            assertThat(feeFor(MemberType.REGULAR, LocalDate.of(2024, 8, 15))).isEqualTo(50.0)
        }

        @Test
        fun `an unresolvable start date pays the full year fee`() {
            assertThat(resolveFeeType(MemberType.REGULAR, null, cutoffDate))
                .isEqualTo(BulkFeeType.FULL_YEAR_FEE)
            assertThat(feeFor(MemberType.REGULAR, null)).isEqualTo(100.0)
        }
    }

    @Nested
    inner class AlumniMembers {

        @Test
        fun `pay the alumni fee regardless of start date`() {
            listOf(LocalDate.of(2023, 1, 1), LocalDate.of(2024, 8, 1), null).forEach { startDate ->
                assertThat(resolveFeeType(MemberType.ALUMNI, startDate, cutoffDate))
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
                assertThat(resolveFeeType(MemberType.HONORARY, startDate, cutoffDate)).isNull()
            }
        }
    }

    @Nested
    inner class AmountRecovery {

        @Test
        fun `each fee option recovers its own type`() {
            assertThat(resolveFeeTypeFromAmount(50.0, period)).isEqualTo(BulkFeeType.HALF_YEAR_FEE)
            assertThat(resolveFeeTypeFromAmount(30.0, period)).isEqualTo(BulkFeeType.ALUMNI_FEE)
            assertThat(resolveFeeTypeFromAmount(100.0, period)).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
        }

        @Test
        fun `an amount matching no fee option falls back to the full year fee`() {
            assertThat(resolveFeeTypeFromAmount(12.34, period)).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
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
