package net.blueshell.api.dto.survey

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.validation.survey.ValidQuestionList
import java.util.function.Function
@Schema(name = "Survey")
class SurveyDTO : BaseDTO() {
    @NotEmpty
    @ValidQuestionList
    @Valid
    var questions: @NotEmpty @Valid MutableList<QuestionDTO?>? = null
    val responseCount: Long? = null

    @get:JsonProperty("questions")
    val questionsSorted: MutableList<QuestionDTO?>
        get() {
            if (questions == null) return mutableListOf<QuestionDTO?>()
            return questions!!.stream()
                .sorted(
                    Comparator
                        .comparing<QuestionDTO?, @NotNull Long?>(
                            Function { obj: QuestionDTO? -> obj!!.idx },
                            Comparator.nullsLast<@NotNull Long?>(Comparator.naturalOrder<@NotNull Long?>())
                        )
                ).toList()
        }
}
