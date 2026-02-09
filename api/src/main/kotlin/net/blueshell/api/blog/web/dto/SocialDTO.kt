package net.blueshell.api.blog.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.dto.BaseDTO

@Schema(name = "Social")
data class SocialDTO(
    var title: String? = null,
    var text: String? = null,
    var url: String? = null,
    var platforms: Array<PlatformType> = emptyArray(),
) : BaseDTO() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SocialDTO

        if (title != other.title) return false
        if (text != other.text) return false
        if (url != other.url) return false
        if (!platforms.contentEquals(other.platforms)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + platforms.contentHashCode()
        return result
    }
}
