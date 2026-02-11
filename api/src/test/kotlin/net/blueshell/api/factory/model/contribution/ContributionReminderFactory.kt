package net.blueshell.api.factory.model.contribution

import com.github.javafaker.Faker
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.factory.model.UserFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for ContributionReminder model test instances.
 */
@Component
class ContributionReminderFactory(
    private val faker: Faker,
    private val userFactory: UserFactory,
    private val contributionPeriodFactory: ContributionPeriodFactory
) {

    fun createBasic(): net.blueshell.api.domain.contribution.persistence.ContributionReminder {
        val reminder = _root_ide_package_.net.blueshell.api.domain.contribution.persistence.ContributionReminder()
        val user = userFactory.createBasic()
        val period = contributionPeriodFactory.createBasic()
        reminder.user = user
        reminder.contributionPeriod = period
        return reminder
    }

    fun createFull(): net.blueshell.api.domain.contribution.persistence.ContributionReminder = createBasic()

    fun createWithCustomizations(customizer: Consumer<net.blueshell.api.domain.contribution.persistence.ContributionReminder>): net.blueshell.api.domain.contribution.persistence.ContributionReminder {
        val reminder = createFull()
        customizer.accept(reminder)
        return reminder
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
