package net.blueshell.api.factory.model.contribution

import com.github.javafaker.Faker
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.factory.model.UserFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Contribution model test instances.
 */
@Component
class ContributionFactory(
    private val faker: Faker,
    private val userFactory: UserFactory,
    private val contributionPeriodFactory: ContributionPeriodFactory
) {

    fun createBasic(): Contribution {
        val contribution = Contribution()
        val user = userFactory.createBasic()
        val period = contributionPeriodFactory.createBasic()
        contribution.user = user
        contribution.contributionPeriod = period
        return contribution
    }

    fun createFull(): Contribution = createBasic()

    fun createWithCustomizations(customizer: Consumer<Contribution>): Contribution {
        val contribution = createFull()
        customizer.accept(contribution)
        return contribution
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
