package net.blueshell.api.mapper.contribution

import net.blueshell.api.factory.dto.contribution.ContributionPeriodDTOFactory
import net.blueshell.api.factory.model.ContributionPeriodFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.contribution.ContributionPeriod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ContributionPeriodMapperIT @Autowired constructor(
    private val contributionPeriodMapper: ContributionPeriodMapper,
    private val contributionPeriodDTOFactory: ContributionPeriodDTOFactory,
) : MapperTestSupport() {
    @Test
    fun `persists fees`() {
        val dto = contributionPeriodDTOFactory.createBasic()
        val period = contributionPeriodFactory.createBasic()

        val mapped = contributionPeriodMapper.fromDTO(dto, period)
        val saved = persist(mapped)
        flushAndClear()

        val reloaded = reload(ContributionPeriod::class.java, saved.id!!)
        val mappedDto = contributionPeriodMapper.toDTO(reloaded)

        assertThat(reloaded.startDate).isEqualTo(dto.startDate)
        assertThat(reloaded.endDate).isEqualTo(dto.endDate)
        assertThat(reloaded.halfYearFee).isEqualTo(dto.halfYearFee)
        assertThat(reloaded.fullYearFee).isEqualTo(dto.fullYearFee)
        assertThat(reloaded.alumniFee).isEqualTo(dto.alumniFee)
        assertThat(mappedDto.id).isEqualTo(saved.id)
    }
}
