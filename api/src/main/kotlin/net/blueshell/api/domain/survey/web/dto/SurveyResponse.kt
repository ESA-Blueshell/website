package net.blueshell.api.domain.survey.web.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import java.util.function.Function

@Schema(name = "SurveyResponse")
data class SurveyResponse(
    @field:NotEmpty
    @field:NotNull
    var questions: MutableList<QuestionResponse>? = null,

    @field:NotNull
    var responseCount: Long? = null
) : AuditedAutoIdDTO() {
    @get:JsonProperty("questions")
    val questionsSorted: MutableList<QuestionResponse?>
        get() {
            if (questions == null) return mutableListOf()
            return questions!!.stream()
                .sorted(
                    Comparator
                        .comparing<QuestionResponse?, @NotNull Long?>(
                            Function { obj: QuestionResponse? -> obj!!.idx },
                            Comparator.nullsLast(Comparator.naturalOrder<@NotNull Long>())
                        )
                ).toList()
        }
}
