package net.blueshell.api.factory.model.contribution

import com.github.javafaker.Faker
import net.blueshell.api.domain.contribution.persistence.Contribution
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

    fun createBasic(): net.blueshell.api.domain.contribution.persistence.Contribution {
        val contribution = _root_ide_package_.net.blueshell.api.domain.contribution.persistence.Contribution()
        val user = userFactory.createBasic()
        val period = contributionPeriodFactory.createBasic()
        contribution.user = user
        contribution.contributionPeriod = period
        return contribution
    }

    fun createFull(): net.blueshell.api.domain.contribution.persistence.Contribution = createBasic()

    fun createWithCustomizations(customizer: Consumer<net.blueshell.api.domain.contribution.persistence.Contribution>): net.blueshell.api.domain.contribution.persistence.Contribution {
        val contribution = createFull()
        customizer.accept(contribution)
        return contribution
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
