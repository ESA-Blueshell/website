package net.blueshell.api.domain.blog.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.dto.BaseDTO
import net.blueshell.api.shared.enums.PlatformType

@Schema(name = "Social")
data class SocialDTO(
    @field:NotNull
    @field:Size(max = 255, message = "Title cannot exceed 255 characters.")
    var title: String? = null,
    @field:NotNull
    @field:Size(max = 4095, message = "Text cannot exceed 4095 characters.")
    var text: String? = null,
    var url: String? = null,

    @field:NotNull
    var platforms: Array<PlatformType>? = null,
) : BaseDTO() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SocialDTO

        if (title != other.title) return false
        if (text != other.text) return false
        if (url != other.url) return false
        if (platforms != null) {
            if (other.platforms == null) return false
            if (!platforms!!.contentEquals(other.platforms)) return false
        } else if (other.platforms != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + (platforms?.contentHashCode() ?: 0)
        return result
    }
}
