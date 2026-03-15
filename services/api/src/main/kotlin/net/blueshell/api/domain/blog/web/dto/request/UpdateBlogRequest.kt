package net.blueshell.api.domain.blog.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

@Schema(name = "UpdateBlogRequest")
data class UpdateBlogRequest(
    @field:NotBlank
    var title: String? = null,

    @field:NotBlank
    var html: String? = null,

    @field:NotNull
    var publishedAt: Instant? = null,

    @field:NotNull
    var version: Long? = null
)
