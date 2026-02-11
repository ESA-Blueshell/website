package net.blueshell.api.contribution.web.dto

import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.contribution.web.mapping.asEntity
import net.blueshell.api.factory.dto.contribution.ContributionReminderDTOFactory
import net.blueshell.api.factory.model.contribution.ContributionReminderFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ContributionReminderDtoIT @Autowired constructor(
    private val contributionReminderDTOFactory: ContributionReminderDTOFactory,
    private val contributionReminderFactory: ContributionReminderFactory,
    private val contributionReminderService: net.blueshell.api.domain.contribution.application.ContributionReminderService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists composite ids`() {
            val user = persistUser()
            val period = persistContributionPeriod()
            val dto = contributionReminderDTOFactory.createBasic().apply {
                userId = user.id!!
                contributionPeriodId = period.id!!
            }
            val reminder = contributionReminderFactory.createBasic().apply {
                this.user = user
                this.contributionPeriod = period
            }

            val mapped = dto.asEntity(reminder)
            val saved = contributionReminderService.create(mapped)
            flushAndClear()

            val reloaded = reload(_root_ide_package_.net.blueshell.api.domain.contribution.persistence.ContributionReminder::class.java, saved.id)

            assertThat(reloaded.userId).isEqualTo(user.id)
            assertThat(reloaded.contributionPeriodId).isEqualTo(period.id)
        }
    }
}
