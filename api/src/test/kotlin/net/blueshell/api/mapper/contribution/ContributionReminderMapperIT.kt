package net.blueshell.api.mapper.contribution

import net.blueshell.api.factory.dto.contribution.ContributionReminderDTOFactory
import net.blueshell.api.factory.model.ContributionReminderFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.contribution.ContributionReminder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ContributionReminderMapperIT @Autowired constructor(
    private val contributionReminderMapper: ContributionReminderMapper,
    private val contributionReminderDTOFactory: ContributionReminderDTOFactory,
    private val contributionReminderFactory: ContributionReminderFactory
) : MapperTestSupport() {
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
        val saved = persist(mapped)
        flushAndClear()

        val reloaded = reload(ContributionReminder::class.java, saved.id)
        val mappedDto = contributionReminderMapper.toDTO(reloaded)

        assertThat(reloaded.userId).isEqualTo(user.id)
        assertThat(reloaded.contributionPeriodId).isEqualTo(period.id)
        assertThat(mappedDto.userId).isEqualTo(user.id)
    }
}
