package net.blueshell.api.contribution.model

import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.contribution.model.ContributionPeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ContributionPeriodModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val period = contributionPeriodFactory.createBasic()
            period.startDate = LocalDate.of(2023, 1, 1)
            period.endDate = LocalDate.of(2023, 12, 31)
            period.halfYearFee = 12.5
            period.fullYearFee = 25.0
            period.alumniFee = 5.0
            period.listId = 99

            val found = persistAndReload(period, ContributionPeriod::class.java) { it.id }

            assertEquals(period.startDate, found.startDate)
            assertEquals(period.endDate, found.endDate)
            assertEquals(period.halfYearFee, found.halfYearFee)
            assertEquals(period.fullYearFee, found.fullYearFee)
            assertEquals(period.alumniFee, found.alumniFee)
            assertEquals(period.listId, found.listId)
        }
    }
}
