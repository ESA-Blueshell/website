package net.blueshell.api.contribution.persistence

import net.blueshell.api.contribution.web.mapping.asDto
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ContributionReminderModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists user relation when setting entity`() {
            val user = persist(userFactory.createBasic())
            val period = persist(contributionPeriodFactory.createBasic())

            val reminder = contributionReminderFactory.createBasic()
            reminder.user = user
            reminder.contributionPeriod = period

            val found = persistAndReload(reminder, ContributionReminder::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user.id)
        }

        @Test
        fun `persists user relation when setting id`() {
            val user = persist(userFactory.createBasic())
            val period = persist(contributionPeriodFactory.createBasic())

            val reminder = contributionReminderFactory.createBasic()
            reminder.userId = user.id!!
            reminder.contributionPeriod = period

            val found = persistAndReload(reminder, ContributionReminder::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user.id)
        }

        @Test
        fun `persists contribution period relation when setting entity`() {
            val user = persist(userFactory.createBasic())
            val period = persist(contributionPeriodFactory.createBasic())

            val reminder = contributionReminderFactory.createBasic()
            reminder.user = user
            reminder.contributionPeriod = period

            val found = persistAndReload(reminder, ContributionReminder::class.java) { it.id }

            assertEquals(period.id, found.contributionPeriodId)
            assertEquals(period.id, found.contributionPeriod.id)
        }

        @Test
        fun `persists contribution period relation when setting id`() {
            val user = persist(userFactory.createBasic())
            val period = persist(contributionPeriodFactory.createBasic())

            val reminder = contributionReminderFactory.createBasic()
            reminder.user = user
            reminder.contributionPeriodId = period.id!!

            val found = persistAndReload(reminder, ContributionReminder::class.java) { it.id }

            assertEquals(period.id, found.contributionPeriodId)
            assertEquals(period.id, found.contributionPeriod.id)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted reminder`() {
            val user = persist(userFactory.createBasic())
            val period = persist(contributionPeriodFactory.createBasic())
            val reminder = persist(contributionReminderFactory.createBasic().apply {
                this.user = user
                this.contributionPeriod = period
            })

            val dto = reminder.asDto()

            assertEquals(reminder.userId, dto.userId)
            assertEquals(reminder.contributionPeriodId, dto.contributionPeriodId)
        }
    }
}
