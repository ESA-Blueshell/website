package net.blueshell.api.event.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.survey.web.dto.SurveyDTO
import java.time.Instant

@Schema(name = "Event")
data class EventDTO(
    @field:NotNull
    var committeeId: Long,

    @field:NotBlank(message = "Event title cannot be empty.")
    @field:Size(max = 255, message = "Event title cannot exceed 255 characters.")
    var title: String,

    @field:NotBlank(message = "Event description cannot be empty.")
    @field:Size(max = 4095, message = "Event description cannot exceed 4095 characters.")
    var description: String,

    var location: String? = null,

    @field:NotNull
    var startTime: Instant? = null,

    @field:NotNull
    var endTime: Instant? = null,

    var memberPrice: Double? = null,
    var publicPrice: Double? = null,
    var approved: Boolean = false,
    var membersOnly: Boolean = false,
    var signUp: Boolean = false,
    var banner: EventBannerDTO? = null,
    var signUpCount: Long? = null,

    @field:Valid
    var signUpForm: SurveyDTO? = null
) : AuditedAutoIdDTO()
