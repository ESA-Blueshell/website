package net.blueshell.api.domain.survey.web.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.survey.web.validation.ValidQuestionList
import java.time.Instant
import java.util.function.Function

@Schema(name = "Survey")
data class SurveyDTO(
    var id: Long? = null,

    @field:NotEmpty
    @field:NotNull
    @field:ValidQuestionList
    @field:Valid
    var questions: MutableList<QuestionDTO>? = null,

    @field:NotNull
    var responseCount: Long? = null,
    var version: Long? = null,
    var createdAt: Instant? = null,
    var updatedAt: Instant? = null,
) {
    @get:JsonProperty("questions")
    val questionsSorted: MutableList<QuestionDTO?>
        get() {
            if (questions == null) return mutableListOf()
            return questions!!.stream()
                .sorted(
                    Comparator
                        .comparing<QuestionDTO?, @NotNull Long?>(
                            Function { obj: QuestionDTO? -> obj!!.idx },
                            Comparator.nullsLast(Comparator.naturalOrder<@NotNull Long>())
                        )
                ).toList()
        }
}
