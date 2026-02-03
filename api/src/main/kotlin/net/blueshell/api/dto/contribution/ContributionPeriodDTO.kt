package net.blueshell.api.dto.contribution

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.base.BaseDTO
import java.time.LocalDate
@Schema(name = "ContributionPeriod")
class ContributionPeriodDTO : BaseDTO() {
    @NotNull
    val startDate: @NotNull LocalDate? = null

    @NotNull
    val endDate: @NotNull LocalDate? = null

    @NotNull
    val halfYearFee: @NotNull Double = 0.0

    @NotNull
    val fullYearFee: @NotNull Double = 0.0

    @NotNull
    val alumniFee: @NotNull Double = 0.0

    val listId: Long? = null
}
