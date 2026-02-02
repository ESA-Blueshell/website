package net.blueshell.api.dto.contribution

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import java.sql.Timestamp

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Contribution")
class ContributionDTO : BaseDTO() {
    private val id: Long? = null

    @NotNull
    private val userId: @NotNull Long? = null

    @NotNull
    private val contributionPeriodId: @NotNull Long? = null

    private val remindedAt: Timestamp? = null
}
