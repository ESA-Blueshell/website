package net.blueshell.api.dto.survey;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.validation.survey.ValidQuestionList;

import java.util.Comparator;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "Survey")
public class SurveyDTO extends BaseDTO {
    private Long id;
    private Long responseCount;

    @NotEmpty
    @ValidQuestionList
    @Valid
    List<QuestionDTO> questions;

    @JsonProperty("questions")
    public List<QuestionDTO> getQuestionsSorted() {
        if (questions == null) return List.of();
        return questions.stream()
                .sorted(Comparator
                        .comparing(QuestionDTO::getIdx, Comparator.nullsLast(Comparator.naturalOrder()))
                ).toList();
    }
}
