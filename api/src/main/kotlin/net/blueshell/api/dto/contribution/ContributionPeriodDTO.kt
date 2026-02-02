package net.blueshell.api.dto.contribution

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import java.time.LocalDate

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "ContributionPeriod")
class ContributionPeriodDTO : BaseDTO() {
    private val id: Long? = null

    @NotNull
    private val startDate: @NotNull LocalDate? = null

    @NotNull
    private val endDate: @NotNull LocalDate? = null

    @NotNull
    private val halfYearFee: @NotNull Double = 0.0

    @NotNull
    private val fullYearFee: @NotNull Double = 0.0

    @NotNull
    private val alumniFee: @NotNull Double = 0.0

    private val listId: Long? = null
}
