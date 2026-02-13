package net.blueshell.api.domain.blog.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import java.time.Instant

@Schema(name = "BlogResponse")
data class BlogResponse(
    var url: String? = null,
    var title: String? = null,
    var html: String? = null,
    var publishedAt: Instant? = null
) : AuditedAutoIdDTO()
