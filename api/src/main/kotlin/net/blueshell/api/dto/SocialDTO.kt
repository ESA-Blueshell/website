package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.common.enums.PlatformType


@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Schema(name = "Social")
class SocialDTO : BaseDTO() {
    private val title: String? = null
    private val text: String? = null
    private val url: String? = null
    private val platforms: Array<PlatformType?>?
}

