package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
@Schema(name = "Guest")
class GuestDTO : PersonalInfoDTO() {
    @NotNull
    val name: @NotNull String? = null
    val accessToken: String? = null
}
