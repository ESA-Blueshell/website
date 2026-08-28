package net.blueshell.api.blog.web

import net.blueshell.api.blog.persistence.Blog

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
