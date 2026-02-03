package net.blueshell.api.dto.contribution

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.base.BaseDTO
import java.sql.Timestamp
@Schema(name = "ContributionReminder")
class ContributionReminderDTO : BaseDTO() {
    @NotNull
    val userId: @NotNull Long? = null

    @NotNull
    val contributionPeriodId: @NotNull Long? = null

    val remindedAt: Timestamp? = null
}
