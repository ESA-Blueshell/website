package net.blueshell.api.dto.event

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.dto.survey.SurveyDTO
import java.time.Instant

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Schema(name = "Event")
class EventDTO : BaseDTO() {
    private val id: Long? = null

    @NotNull
    private val committeeId: @NotNull Long? = null

    @NotBlank(message = "Event title cannot be empty.")
    @Size(max = 255, message = "Event title cannot exceed 255 characters.")
    private val title: @NotBlank(message = "Event title cannot be empty.") @Size(
        max = 255,
        message = "Event title cannot exceed 255 characters."
    ) String? = null

    @NotBlank(message = "Event description cannot be empty.")
    @Size(max = 4095, message = "Event description cannot exceed 4095 characters.")
    private val description: @NotBlank(message = "Event description cannot be empty.") @Size(
        max = 4095,
        message = "Event description cannot exceed 4095 characters."
    ) String? = null

    @JsonProperty("location")
    private val location: String? = null

    @NotNull
    private val startTime: @NotNull Instant? = null

    @NotNull
    private val endTime: @NotNull Instant? = null
    private val memberPrice: Double? = null
    private val publicPrice: Double? = null
    private val approved = false
    private val membersOnly = false
    private val signUp = false
    private val banner: EventBannerDTO? = null
    private val signUpCount: Long? = null

    @Valid
    private val signUpForm: @Valid SurveyDTO? = null
}
