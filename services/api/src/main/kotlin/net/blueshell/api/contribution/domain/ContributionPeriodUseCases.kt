package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import org.springframework.stereotype.Service
import java.time.LocalDate
import net.blueshell.api.contribution.api.ContributionPeriodService

/** Building and mutating a contribution period. Reads and deletes go to the service. */
@Service
class ContributionPeriodUseCases(
    private val service: ContributionPeriodService,
) {
    fun create(
        startDate: LocalDate,
        endDate: LocalDate,
        halfYearCutoffDate: LocalDate,
        halfYearFee: Double,
        fullYearFee: Double,
        alumniFee: Double,
        contactListId: Long?,
    ): ContributionPeriod =
        service.create(
            ContributionPeriod(
                startDate = startDate,
                endDate = endDate,
                halfYearCutoffDate = halfYearCutoffDate,
                halfYearFee = halfYearFee,
                fullYearFee = fullYearFee,
                alumniFee = alumniFee,
                contactListId = contactListId,
            ),
        )

    fun update(
        id: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        halfYearCutoffDate: LocalDate,
        halfYearFee: Double,
        fullYearFee: Double,
        alumniFee: Double,
        contactListId: Long?,
        version: Long,
    ): ContributionPeriod {
        val period = service.findById(id).apply {
            this.startDate = startDate
            this.endDate = endDate
            this.halfYearCutoffDate = halfYearCutoffDate
            this.halfYearFee = halfYearFee
            this.fullYearFee = fullYearFee
            this.alumniFee = alumniFee
            this.contactListId = contactListId
            this.version = version
        }
        return service.update(period)
    }
}
