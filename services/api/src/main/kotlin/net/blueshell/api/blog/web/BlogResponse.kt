package net.blueshell.api.blog.web

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "BlogResponse")
data class BlogResponse(
    var id: Long,
    var url: String,
    var title: String,
    var html: String,
    var publishedAt: Instant,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
