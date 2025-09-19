package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Contribution")
public class ContributionDTO extends BaseDTO {
    private Long id;

    private Long userId;

    private Long contributionPeriodId;

    private Boolean paid;

    private Timestamp remindedAt;
}
