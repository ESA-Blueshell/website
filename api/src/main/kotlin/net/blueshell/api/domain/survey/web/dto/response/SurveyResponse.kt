package net.blueshell.api.domain.survey.web.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.Instant

@Schema(name = "SurveyResponse")
data class SurveyResponse(
    var id: Long,

    @field:NotEmpty
    @field:NotNull
    var questions: MutableList<QuestionResponse>,

    @field:NotNull
    var responseCount: Long,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
) {
    @get:JsonProperty("questions")
    val questionsSorted: MutableList<QuestionResponse>
        get() {
            return questions.stream()
                .sorted(
                    Comparator
                        .comparing(QuestionResponse::idx)
                ).toList()
        }
}
