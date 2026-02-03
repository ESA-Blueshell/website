package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.base.BaseDTO
import java.time.Instant
@Schema(name = "Blog")
class BlogDTO : BaseDTO() {
    val url: String? = null

    @NotBlank
    val title: @NotBlank String? = null

    @NotBlank
    val html: @NotBlank String? = null
    val publishedAt: Instant? = null
}

