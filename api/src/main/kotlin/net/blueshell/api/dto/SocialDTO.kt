package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.dto.BaseDTO
import net.blueshell.api.common.enums.PlatformType

@Schema(name = "Social")
data class SocialDTO(
    var title: String? = null,
    var text: String? = null,
    var url: String? = null,
    var platforms: Array<PlatformType> = emptyArray(),
) : BaseDTO()
