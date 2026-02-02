package net.blueshell.api.dto.event

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.dto.FileDTO

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Schema(name = "EventBanner")
class EventBannerDTO : BaseDTO() {
    private val id: Long? = null

    @NotNull
    private val file: @NotNull FileDTO? = null
}
