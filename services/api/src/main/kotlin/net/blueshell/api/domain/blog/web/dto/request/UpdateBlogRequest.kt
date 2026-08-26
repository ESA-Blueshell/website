package net.blueshell.api.domain.blog.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

@Schema(name = "UpdateBlogRequest")
data class UpdateBlogRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    var title: String,

    @field:NotBlank
    var html: String,

    var publishedAt: Instant,

    var version: Long
)
