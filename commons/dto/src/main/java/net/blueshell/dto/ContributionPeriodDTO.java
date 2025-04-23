package net.blueshell.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = false)
public class ContributionPeriodDTO extends BaseDTO {
    private Long id;

    private LocalDate startDate;

    private LocalDate endDate;

    private double halfYearFee;

    private double fullYearFee;

    private double alumniFee;

    private Long listId;
}
