package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "ContributionPeriod")
public class ContributionPeriodDTO extends BaseDTO {
    @Nullable
    private Long id;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    private double halfYearFee;

    @NotNull
    private double fullYearFee;

    @NotNull
    private double alumniFee;

    private Long listId;
}
