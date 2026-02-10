package net.blueshell.api.blog.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import java.time.Instant

@Schema(name = "Blog")
data class BlogDTO(
    var url: String? = null,

    @field:NotBlank
    var title: String? = null,

    @field:NotBlank
    var html: String? = null,

    @field:NotNull
    var publishedAt: Instant? = null
) : AuditedAutoIdDTO()
