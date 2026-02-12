package net.blueshell.api.domain.blog.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

@Schema(name = "CreateBlogRequest")
data class CreateBlogRequest(
    @field:NotBlank
    var title: String? = null,

    @field:NotBlank
    var html: String? = null,

    @field:NotNull
    var publishedAt: Instant? = null
)
