package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.MembershipType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class OwedTest {
    private val period =
        ContributionPeriod(
            startDate = LocalDate.of(2025, 9, 1),
            endDate = LocalDate.of(2026, 8, 31),
            halfYearCutoffDate = LocalDate.of(2026, 2, 1),
            halfYearFee = 15.0,
            fullYearFee = 30.0,
            alumniFee = 10.0,
        )

    private fun membership(vararg types: Pair<MemberType, LocalDate>): Membership =
        Membership(user = Entities.user(), startDate = LocalDate.of(2022, 9, 1)).apply {
            // Added newest first, as nothing promises an order before the rows are read back.
            val held = (listOf(MemberType.REGULAR to startDate) + types).reversed()
            held.forEach { (type, since) -> addType(MembershipType(this, type, since)) }
            memberType = types.lastOrNull()?.first ?: MemberType.REGULAR
        }

    private fun ask(
        feeType: BulkFeeType,
        amount: Double,
        on: String,
    ) = Ask(feeType, amount, Instant.parse("${on}T10:00:00Z"))

    @Test
    fun `before any ask a member owes what their membership type today would be asked`() {
        assertThat(owed(membership(), period, emptyList())).isEqualTo(Owed(BulkFeeType.FULL_YEAR_FEE, 30.0))
        assertThat(owed(membership(MemberType.ALUMNI to LocalDate.of(2026, 1, 1)), period, emptyList()))
            .isEqualTo(Owed(BulkFeeType.ALUMNI_FEE, 10.0))
    }

    @Test
    fun `a member asked twice owes the latest ask, at the amount it said`() {
        val asks = listOf(ask(BulkFeeType.ALUMNI_FEE, 12.0, "2026-02-01"), ask(BulkFeeType.FULL_YEAR_FEE, 28.0, "2025-10-01"))

        assertThat(owed(membership(), period, asks)).isEqualTo(Owed(BulkFeeType.ALUMNI_FEE, 12.0))
    }

    @Test
    fun `turning honorary within the period drops it, asked or not`() {
        val honorary = membership(MemberType.HONORARY to LocalDate.of(2026, 2, 15))

        assertThat(owed(honorary, period, listOf(ask(BulkFeeType.FULL_YEAR_FEE, 30.0, "2025-10-01")))).isNull()
        assertThat(owed(honorary, period, emptyList())).isNull()
    }

    @Test
    fun `a period that ended before the member turned honorary stays owed, at the type they held then`() {
        val earlier =
            ContributionPeriod(
                startDate = LocalDate.of(2024, 9, 1),
                endDate = LocalDate.of(2025, 8, 31),
                halfYearCutoffDate = LocalDate.of(2025, 2, 1),
                halfYearFee = 15.0,
                fullYearFee = 30.0,
                alumniFee = 10.0,
            )
        val honorary = membership(MemberType.HONORARY to LocalDate.of(2026, 2, 15))

        assertThat(owed(honorary, earlier, emptyList())).isEqualTo(Owed(BulkFeeType.FULL_YEAR_FEE, 30.0))
    }
}
