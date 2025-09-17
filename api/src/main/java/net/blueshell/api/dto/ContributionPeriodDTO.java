package net.blueshell.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = false)
public class ContributionPeriodDTO extends BaseDTO {
    private Long id;

    @NotBlank
    private LocalDate startDate;

    @NotBlank
    private LocalDate endDate;

    @NotBlank
    private double halfYearFee;

    @NotBlank
    private double fullYearFee;

    @NotBlank
    private double alumniFee;

    private Long listId;
}
