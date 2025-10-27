package net.blueshell.api.dto.survey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.validation.survey.ValidQuestion;

import java.util.List;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Schema(name = "Question")
@ValidQuestion
public class QuestionDTO extends BaseDTO {
    private Long id;
    @NotNull
    private Long idx;
    private Long surveyId;
    @NotNull
    private QuestionType type;
    @NotBlank(message = "Label cannot be empty.")
    @Size(max = 2055, message = "Label cannot exceed 2055 characters.")
    private String label;
    private List<String> choiceLabels;
}
