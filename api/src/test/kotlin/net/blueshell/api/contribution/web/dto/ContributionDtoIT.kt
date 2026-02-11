package net.blueshell.api.contribution.web.dto

import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.web.mapping.asEntity
import net.blueshell.api.factory.dto.contribution.ContributionDTOFactory
import net.blueshell.api.factory.model.contribution.ContributionFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ContributionDtoIT @Autowired constructor(
    private val contributionDTOFactory: ContributionDTOFactory,
    private val contributionFactory: ContributionFactory,
    private val contributionService: ContributionService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
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

            val mapped = dto.asEntity(contribution)
            val saved = contributionService.create(mapped)
            flushAndClear()

            val reloaded = reload(Contribution::class.java, saved.id)

            assertThat(reloaded.userId).isEqualTo(user.id)
            assertThat(reloaded.contributionPeriodId).isEqualTo(period.id)
        }
    }
}
