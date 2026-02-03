package net.blueshell.api.dto.event

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.dto.survey.SurveyDTO
import java.time.Instant
@Schema(name = "Event")
class EventDTO : BaseDTO() {
    @NotNull
    val committeeId: @NotNull Long? = null

    @NotBlank(message = "Event title cannot be empty.")
    @Size(max = 255, message = "Event title cannot exceed 255 characters.")
    val title: @NotBlank(message = "Event title cannot be empty.") @Size(
        max = 255,
        message = "Event title cannot exceed 255 characters."
    ) String? = null

    @NotBlank(message = "Event description cannot be empty.")
    @Size(max = 4095, message = "Event description cannot exceed 4095 characters.")
    val description: @NotBlank(message = "Event description cannot be empty.") @Size(
        max = 4095,
        message = "Event description cannot exceed 4095 characters."
    ) String? = null

    @JsonProperty("location")
    val location: String? = null

    @NotNull
    val startTime: @NotNull Instant? = null

    @NotNull
    val endTime: @NotNull Instant? = null
    val memberPrice: Double? = null
    val publicPrice: Double? = null
    val approved = false
    val membersOnly = false
    val signUp = false
    val banner: EventBannerDTO? = null
    val signUpCount: Long? = null

    @Valid
    val signUpForm: @Valid SurveyDTO? = null
}
