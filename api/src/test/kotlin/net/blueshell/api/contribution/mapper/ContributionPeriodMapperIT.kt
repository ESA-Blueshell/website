package net.blueshell.api.contribution.mapper

import net.blueshell.api.factory.dto.contribution.ContributionPeriodDTOFactory
import net.blueshell.api.factory.model.contribution.ContributionPeriodFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.contribution.mapper.ContributionPeriodMapper
import net.blueshell.api.contribution.model.ContributionPeriod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ContributionPeriodMapperIT @Autowired constructor(
    private val contributionPeriodMapper: ContributionPeriodMapper,
    private val contributionPeriodDTOFactory: ContributionPeriodDTOFactory,
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted period`() {
            val period = persist(contributionPeriodFactory.createBasic())

            val dto = contributionPeriodMapper.toDTO(period)

            assertThat(dto.id).isEqualTo(period.id)
            assertThat(dto.startDate).isEqualTo(period.startDate)
            assertThat(dto.endDate).isEqualTo(period.endDate)
            assertThat(dto.halfYearFee).isEqualTo(period.halfYearFee)
            assertThat(dto.fullYearFee).isEqualTo(period.fullYearFee)
            assertThat(dto.alumniFee).isEqualTo(period.alumniFee)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists fees`() {
            val dto = contributionPeriodDTOFactory.createBasic()
            val period = contributionPeriodFactory.createBasic()

            val mapped = contributionPeriodMapper.fromDTO(dto, period)
            val saved = persist(mapped)
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
