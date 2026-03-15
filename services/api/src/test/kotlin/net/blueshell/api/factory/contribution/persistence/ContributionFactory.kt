package net.blueshell.api.factory.contribution.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ContributionFactory(
    private val persistence: FactoryPersistenceSupport
) {
    fun buildPeriod(
        startDate: LocalDate = LocalDate.now().minusMonths(1),
        endDate: LocalDate = LocalDate.now().plusMonths(1)
    ): ContributionPeriod {
        return ContributionPeriod(
            startDate = startDate,
            endDate = endDate,
            halfYearFee = 25.0,
            fullYearFee = 45.0,
            alumniFee = 10.0,
        )
    }

    fun createPeriod(
        startDate: LocalDate = LocalDate.now().minusMonths(1),
        endDate: LocalDate = LocalDate.now().plusMonths(1)
    ): ContributionPeriod {
        return persistence.persist(buildPeriod(startDate, endDate))
    }
}
