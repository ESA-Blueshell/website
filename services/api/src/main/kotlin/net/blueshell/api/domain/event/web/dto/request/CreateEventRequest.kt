package net.blueshell.api.domain.event.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.domain.event.web.validation.HasSignUpDeadline
import net.blueshell.api.domain.event.web.validation.ValidSignUpDeadline
import net.blueshell.api.domain.survey.web.dto.request.SurveyRequest
import java.time.Instant

@Schema(name = "CreateEventRequest")
@ValidSignUpDeadline
data class CreateEventRequest(
    @field:NotNull
    var committeeId: Long? = null,

    @field:NotBlank(message = "Event title cannot be empty.")
    @field:Size(max = 255, message = "Event title cannot exceed 255 characters.")
    var title: String? = null,

    @field:NotBlank(message = "Event description cannot be empty.")
    @field:Size(max = 4095, message = "Event description cannot exceed 4095 characters.")
    var description: String? = null,

    var location: String? = null,

    @field:NotNull
    var startTime: Instant? = null,

    @field:NotNull
    override var endTime: Instant? = null,

    var memberPrice: Double? = null,
    var publicPrice: Double? = null,

    @field:NotNull
    var approved: Boolean? = null,

    @field:NotNull
    var membersOnly: Boolean? = null,

    @field:NotNull
    var signUp: Boolean? = null,

    override var signUpDeadline: Instant? = null,

    @field:Min(1, message = "Sign-up limit must be at least 1")
    var signUpLimit: Int? = null,

    @field:Valid
    var banner: EventBannerRequest? = null,

    @field:Valid
    var signUpForm: SurveyRequest? = null
) : HasSignUpDeadline
