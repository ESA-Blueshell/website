package net.blueshell.api.domain.contribution.application

import net.blueshell.api.domain.contribution.application.result.ContributionPeriodResult
import net.blueshell.api.domain.contribution.application.result.toResult
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import org.springframework.stereotype.Service
import java.time.LocalDate

/** Building and mutating a contribution period. Reads and deletes go to the service. */
@Service
class ContributionPeriodUseCases(
    private val service: ContributionPeriodService,
) {
    fun create(
        startDate: LocalDate,
        endDate: LocalDate,
        halfYearFee: Double,
        fullYearFee: Double,
        alumniFee: Double,
        contactListId: Long?,
    ): ContributionPeriodResult =
        service.create(
            ContributionPeriod(
                startDate = startDate,
                endDate = endDate,
                halfYearFee = halfYearFee,
                fullYearFee = fullYearFee,
                alumniFee = alumniFee,
                contactListId = contactListId,
            ),
        ).toResult()

    fun update(
        id: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        halfYearFee: Double,
        fullYearFee: Double,
        alumniFee: Double,
        contactListId: Long?,
        version: Long,
    ): ContributionPeriodResult {
        val period = service.findById(id).apply {
            this.startDate = startDate
            this.endDate = endDate
            this.halfYearFee = halfYearFee
            this.fullYearFee = fullYearFee
            this.alumniFee = alumniFee
            this.contactListId = contactListId
            this.version = version
        }
        return service.update(period).toResult()
    }
}
