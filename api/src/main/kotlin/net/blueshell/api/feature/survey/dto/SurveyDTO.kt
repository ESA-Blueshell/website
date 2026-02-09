package net.blueshell.api.feature.survey.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.feature.survey.validation.ValidQuestionList
import java.util.function.Function

@Schema(name = "Survey")
data class SurveyDTO(
    @field:NotEmpty
    @field:ValidQuestionList
    @field:Valid
    var questions: MutableList<QuestionDTO> = mutableListOf(),
    var responseCount: Long? = null
) : AuditedAutoIdDTO() {
    @get:JsonProperty("questions")
    val questionsSorted: MutableList<QuestionDTO?>
        get() {
            return questions.stream()
                .sorted(
                    Comparator
                        .comparing<QuestionDTO?, @NotNull Long?>(
                            Function { obj: QuestionDTO? -> obj!!.idx },
                            Comparator.nullsLast(Comparator.naturalOrder<@NotNull Long>())
                        )
                ).toList()
        }
}
