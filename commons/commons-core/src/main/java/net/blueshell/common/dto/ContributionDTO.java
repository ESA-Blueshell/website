package net.blueshell.common.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = false)
public class ContributionDTO extends BaseDTO {
    private Long id;

    private Long userId;

    private Long contributionPeriodId;

    private Boolean paid;

    private Timestamp remindedAt;
}
