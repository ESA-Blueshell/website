package net.blueshell.api.dto.event

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.dto.FileDTO
@Schema(name = "EventBanner")
class EventBannerDTO : BaseDTO() {
    @NotNull
    val file: @NotNull FileDTO? = null
}
