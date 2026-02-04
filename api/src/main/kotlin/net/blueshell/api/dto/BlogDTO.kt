package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.base.dto.AuditedAutoIdDTO
import java.time.Instant

@Schema(name = "Blog")
data class BlogDTO(
    var url: String? = null,

    @field:NotBlank
    var title: String? = null,

    @field:NotBlank
    var html: String? = null,

    var publishedAt: Instant? = null
) : AuditedAutoIdDTO()
