package net.blueshell.api.domain.contribution.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import java.time.LocalDate

@Schema(name = "ContributionPeriodResponse")
data class ContributionPeriodResponse(
    var startDate: LocalDate? = null,
    var endDate: LocalDate? = null,
    var halfYearFee: Double? = null,
    var fullYearFee: Double? = null,
    var alumniFee: Double? = null,
    var listId: Long? = null
) : AuditedAutoIdDTO()
