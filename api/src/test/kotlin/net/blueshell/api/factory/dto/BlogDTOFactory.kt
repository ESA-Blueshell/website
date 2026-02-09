package net.blueshell.api.factory.dto

import net.blueshell.api.blog.api.dto.BlogDTO
import org.springframework.stereotype.Component

/**
 * Factory for BlogDTO test instances.
 */
@Component
class BlogDTOFactory : BaseDtoFactory<BlogDTO>() {

    override fun targetType(): Class<BlogDTO> = BlogDTO::class.java

    override fun createBasic(): BlogDTO {
        val dto = BlogDTO()
        dto.title = unique("Blog Title")
        dto.html = "<p>Test content</p>"
        dto.publishedAt = now()
        dto.url = "https://example.com/blog/${nextId()}"
        return dto
    }
}
