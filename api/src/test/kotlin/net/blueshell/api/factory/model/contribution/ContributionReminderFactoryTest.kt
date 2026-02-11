package net.blueshell.api.factory.model.contribution

import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.factory.model.ModelFactoryTestSupport
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
        assertPersisted(_root_ide_package_.net.blueshell.api.domain.contribution.persistence.ContributionReminder::class.java, saved.id)
    }
}
