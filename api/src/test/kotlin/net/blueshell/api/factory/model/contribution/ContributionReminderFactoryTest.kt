package net.blueshell.api.factory.model.contribution

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.contribution.domain.model.ContributionReminder
import org.junit.jupiter.api.Test

class ContributionReminderFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable contribution reminder`() {
        val period = persistContributionPeriod()
        val user = persistUser()

        val reminder = contributionReminderFactory.createBasic()
        reminder.user = user
        reminder.contributionPeriod = period

        val saved = persist(reminder)
        assertPersisted(ContributionReminder::class.java, saved.id)
    }
}
