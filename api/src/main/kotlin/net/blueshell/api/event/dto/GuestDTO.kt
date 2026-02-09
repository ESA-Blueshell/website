package net.blueshell.api.event.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.user.dto.PersonalInfoDTO

@Schema(name = "Guest")
data class GuestDTO(
    @field:NotNull
    var name: String? = null,
) : PersonalInfoDTO() {
    lateinit var accessToken: String
}
