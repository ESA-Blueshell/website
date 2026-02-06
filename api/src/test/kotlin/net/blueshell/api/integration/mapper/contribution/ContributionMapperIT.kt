package net.blueshell.api.integration.mapper.contribution

import net.blueshell.api.factory.dto.contribution.ContributionDTOFactory
import net.blueshell.api.factory.model.contribution.ContributionFactory
import net.blueshell.api.integration.mapper.MapperTestSupport
import net.blueshell.api.mapper.contribution.ContributionMapper
import net.blueshell.api.model.contribution.Contribution
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ContributionMapperIT @Autowired constructor(
    private val contributionMapper: ContributionMapper,
    private val contributionDTOFactory: ContributionDTOFactory,
    private val contributionFactory: ContributionFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted contribution`() {
            val user = persistUser()
            val period = persistContributionPeriod()
            val contribution = persist(contributionFactory.createBasic().apply {
                this.user = user
                this.contributionPeriod = period
            })

            val dto = contributionMapper.toDTO(contribution)

            assertThat(dto.userId).isEqualTo(contribution.userId)
            assertThat(dto.contributionPeriodId).isEqualTo(contribution.contributionPeriodId)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists composite ids`() {
            val user = persistUser()
            val period = persistContributionPeriod()
            val dto = contributionDTOFactory.createBasic().apply {
                userId = user.id!!
                contributionPeriodId = period.id!!
            }
            val contribution = contributionFactory.createBasic().apply {
                this.user = user
                this.contributionPeriod = period
            }

            val mapped = contributionMapper.fromDTO(dto, contribution)
            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(Contribution::class.java, saved.id)

            assertThat(reloaded.userId).isEqualTo(user.id)
            assertThat(reloaded.contributionPeriodId).isEqualTo(period.id)
        }
    }
}
