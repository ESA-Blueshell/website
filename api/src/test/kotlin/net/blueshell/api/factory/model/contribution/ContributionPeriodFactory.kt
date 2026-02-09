package net.blueshell.api.factory.model.contribution

import com.github.javafaker.Faker
import net.blueshell.api.contribution.persistence.ContributionPeriod
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for ContributionPeriod model test instances.
 */
@Component
class ContributionPeriodFactory(
    private val faker: Faker
) {

    fun createBasic(): ContributionPeriod {
        val period = ContributionPeriod()
        val start = LocalDate.now().withDayOfMonth(1)
        period.startDate = start
        period.endDate = start.plusMonths(6)
        period.halfYearFee = 10.0
        period.fullYearFee = 18.0
        period.alumniFee = 5.0
        period.listId = faker.number().randomNumber()
        return period
    }

    fun createFull(): ContributionPeriod = createBasic()

    fun createWithCustomizations(customizer: Consumer<ContributionPeriod>): ContributionPeriod {
        val period = createFull()
        customizer.accept(period)
        return period
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
