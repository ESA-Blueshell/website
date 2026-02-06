package net.blueshell.api.factory.model

import com.github.javafaker.Faker
import net.blueshell.api.model.contribution.ContributionReminder
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

    fun createBasic(): ContributionReminder {
        val reminder = ContributionReminder()
        val user = userFactory.createBasic()
        val period = contributionPeriodFactory.createBasic()
        reminder.user = user
        reminder.contributionPeriod = period
        return reminder
    }

    fun createFull(): ContributionReminder = createBasic()

    fun createWithCustomizations(customizer: Consumer<ContributionReminder>): ContributionReminder {
        val reminder = createFull()
        customizer.accept(reminder)
        return reminder
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
