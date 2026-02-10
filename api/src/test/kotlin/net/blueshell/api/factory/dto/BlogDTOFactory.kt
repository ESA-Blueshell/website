package net.blueshell.api.factory.dto

import net.blueshell.api.blog.web.dto.BlogDTO
import org.springframework.stereotype.Component

/**
 * Factory for BlogDTO test instances.
 */
@Component
class BlogDTOFactory : BaseDtoFactory<BlogDTO>() {

    override fun targetType(): Class<BlogDTO> = BlogDTO::class.java

    override fun createBasic(): BlogDTO {
        val dto = BlogDTO(
            title = unique("Blog Title"),
            html = "<p>Test content</p>",
            publishedAt = now(),
            url = "https://example.com/blog/${nextId()}"
        )
        return dto
    }
}
