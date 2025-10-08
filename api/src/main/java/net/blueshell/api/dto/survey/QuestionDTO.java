package net.blueshell.api.dto.survey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.common.enums.QuestionType;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "Question")
public class QuestionDTO extends BaseDTO {
    private Long id;
    @NotNull
    private Long idx;
    private Long surveyId;
    @NotNull
    private QuestionType type;
    private String label;
    private List<String> choiceLabels;
}
