package net.blueshell.api.dto.contribution;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "ContributionReminder")
public class ContributionReminderDTO extends BaseDTO {
    private Long id;

    @NotNull
    private Long userId;

    @NotNull
    private Long contributionPeriodId;

    private Timestamp remindedAt;
}
