package net.blueshell.api.domain.blog.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Schema(name = "UpdateBlogRequest")
data class UpdateBlogRequest(
    @field:NotBlank
    var title: String,

    @field:NotBlank
    var html: String,

    var publishedAt: Instant,

    var version: Long
)
