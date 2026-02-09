package net.blueshell.api.contribution.web.mapper

import net.blueshell.api.factory.dto.contribution.ContributionReminderDTOFactory
import net.blueshell.api.factory.model.contribution.ContributionReminderFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.contribution.web.mapper.ContributionReminderMapper
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.application.ContributionReminderService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ContributionReminderMapperIT @Autowired constructor(
    private val contributionReminderMapper: ContributionReminderMapper,
    private val contributionReminderDTOFactory: ContributionReminderDTOFactory,
    private val contributionReminderFactory: ContributionReminderFactory,
    private val contributionReminderService: ContributionReminderService
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted reminder`() {
            val user = persistUser()
            val period = persistContributionPeriod()
            val reminder = persist(contributionReminderFactory.createBasic().apply {
                this.user = user
                this.contributionPeriod = period
            })

            val dto = contributionReminderMapper.toDTO(reminder)

            assertThat(dto.userId).isEqualTo(reminder.userId)
            assertThat(dto.contributionPeriodId).isEqualTo(reminder.contributionPeriodId)
        }
    }

    @Nested
    inner class FromDTO {
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

            val mapped = contributionReminderMapper.fromDTO(dto, reminder)
            val saved = contributionReminderService.create(mapped)
            flushAndClear()

            val reloaded = reload(ContributionReminder::class.java, saved.id)

            assertThat(reloaded.userId).isEqualTo(user.id)
            assertThat(reloaded.contributionPeriodId).isEqualTo(period.id)
        }
    }
}
