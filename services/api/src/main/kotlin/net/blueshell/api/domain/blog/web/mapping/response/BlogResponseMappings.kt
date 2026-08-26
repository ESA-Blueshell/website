package net.blueshell.api.domain.blog.web.mapping.response

import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.domain.blog.web.dto.response.BlogResponse

fun Blog.asResponse(frontendUrl: String): BlogResponse =
    BlogResponse(
        id = this.id!!,
        url = "$frontendUrl/blogs/${this.id!!}",
        title = this.title,
        html = this.html,
        publishedAt = this.publishedAt,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
