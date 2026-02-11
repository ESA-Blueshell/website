package net.blueshell.api.domain.contribution.persistence

import net.blueshell.api.domain.contribution.web.mapping.asDto
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
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

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted period`() {
            val period = persist(contributionPeriodFactory.createBasic())

            val dto = period.asDto()

            assertEquals(period.id, dto.id)
            assertEquals(period.startDate, dto.startDate)
            assertEquals(period.endDate, dto.endDate)
            assertEquals(period.halfYearFee, dto.halfYearFee)
            assertEquals(period.fullYearFee, dto.fullYearFee)
            assertEquals(period.alumniFee, dto.alumniFee)
        }
    }
}
