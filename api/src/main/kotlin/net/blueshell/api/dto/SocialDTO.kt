package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.common.enums.PlatformType
@Schema(name = "Social")
class SocialDTO : BaseDTO() {
    val title: String? = null
    val text: String? = null
    val url: String? = null
    val platforms: Array<PlatformType?>?
}

