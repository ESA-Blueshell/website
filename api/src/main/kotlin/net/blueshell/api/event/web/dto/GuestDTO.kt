package net.blueshell.api.event.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.user.web.dto.PersonalInfoDTO

@Schema(name = "Guest")
data class GuestDTO(
    @field:NotNull
    var name: String,
    override var email: String,
) : PersonalInfoDTO(email) {
    var accessToken: String? = null
}
