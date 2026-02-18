package net.blueshell.api.factory.blog.web.request

import org.springframework.stereotype.Component

@Component
class BlogRequestFactory {
    fun createPayload(title: String, html: String, publishedAt: String): String =
        """{"title":"$title","html":"$html","publishedAt":"$publishedAt"}"""

    fun updatePayload(version: Long, title: String, html: String, publishedAt: String): String =
        """{"title":"$title","html":"$html","publishedAt":"$publishedAt","version":$version}"""
}
