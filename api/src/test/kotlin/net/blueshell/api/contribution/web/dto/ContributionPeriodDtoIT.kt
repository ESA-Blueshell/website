package net.blueshell.api.contribution.web.dto

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.web.mapping.asEntity
import net.blueshell.api.factory.dto.contribution.ContributionPeriodDTOFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ContributionPeriodDtoIT @Autowired constructor(
    private val contributionPeriodDTOFactory: ContributionPeriodDTOFactory,
    private val contributionPeriodService: ContributionPeriodService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists fees`() {
            val dto = contributionPeriodDTOFactory.createBasic()
            val period = contributionPeriodFactory.createBasic()

            val mapped = dto.asEntity(period)
            val saved = contributionPeriodService.create(mapped)
            flushAndClear()

            val reloaded = reload(ContributionPeriod::class.java, saved.id!!)

            assertThat(reloaded.startDate).isEqualTo(dto.startDate)
            assertThat(reloaded.endDate).isEqualTo(dto.endDate)
            assertThat(reloaded.halfYearFee).isEqualTo(dto.halfYearFee)
            assertThat(reloaded.fullYearFee).isEqualTo(dto.fullYearFee)
            assertThat(reloaded.alumniFee).isEqualTo(dto.alumniFee)
        }
    }
}
