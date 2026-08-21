package net.blueshell.api.domain.blog.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Schema(name = "CreateBlogRequest")
data class CreateBlogRequest(
    @field:NotBlank
    var title: String,

    @field:NotBlank
    var html: String,

    var publishedAt: Instant
)
