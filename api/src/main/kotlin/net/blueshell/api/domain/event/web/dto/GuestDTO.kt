package net.blueshell.api.domain.event.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.user.web.dto.PersonalInfoDTO

@Schema(name = "Guest")
data class GuestDTO(
    @field:NotNull
    var name: String? = null,
) : PersonalInfoDTO() {
    var accessToken: String? = null
}
