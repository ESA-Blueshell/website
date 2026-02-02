package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import lombok.Data
import lombok.EqualsAndHashCode

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Guest")
class GuestDTO : PersonalInfoDTO() {
    private val id: Long? = null

    @NotNull
    private val name: @NotNull String? = null
    private val accessToken: String? = null
}
