package net.blueshell.api.dto.survey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.model.survey.Question;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "Survey")
public class SurveyDTO extends BaseDTO {
    private Long id;

    @NotEmpty
    private Set<Question> questions;
}
