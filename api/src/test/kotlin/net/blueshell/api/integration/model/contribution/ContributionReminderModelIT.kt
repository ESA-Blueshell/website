package net.blueshell.api.integration.model.contribution

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ContributionReminderModelIT : net.blueshell.api.integration.model.ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_join_columns() {
            val user = persist(userFactory.createBasic())
            val period = persist(contributionPeriodFactory.createBasic())

            val reminder = contributionReminderFactory.createBasic()
            reminder.user = user
            reminder.contributionPeriod = period

            val found = persistAndReload(reminder, ContributionReminder::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(period.id, found.contributionPeriodId)
        }
    }
}
