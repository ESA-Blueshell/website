package net.blueshell.api.dto.survey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.validation.survey.ValidAnswer;

import java.util.List;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Schema(name = "Answer")
@ValidAnswer
public class AnswerDTO extends BaseDTO {
    private Long id;

    @NotNull
    private Long questionId;

    private List<Boolean> optionSelections;

    private String textResponse;
}
