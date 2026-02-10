package net.blueshell.api.blog.persistence

import net.blueshell.api.blog.web.mapping.asDto
import net.blueshell.api.blog.web.mapping.asSocialDto
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value

class BlogModelIT : ModelPersistenceTestSupport() {
    @Value($$"${frontend.url}")
    private lateinit var frontendUrl: String

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val blog = blogFactory.createBasic()
            blog.title = unique("blog")
            blog.html = "<p>${unique("html")}</p>"
            blog.publishedAt = timestamp()

            val found = persistAndReload(blog, Blog::class.java) { it.id }

            assertEquals(blog.title, found.title)
            assertEquals(blog.html, found.html)
            assertEquals(blog.publishedAt, found.publishedAt)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted blog`() {
            val blog = persist(blogFactory.createBasic())

            val dto = blog.asDto(frontendUrl)

            assertEquals(blog.id, dto.id)
            assertEquals(blog.title, dto.title)
            assertEquals(blog.html, dto.html)
            assertEquals(blog.publishedAt, dto.publishedAt)
            assertEquals(true, dto.url!!.contains("/blogs/${blog.id}"))
        }

        @Test
        fun `builds social dto from blog`() {
            val blog = persist(blogFactory.createBasic())

            val dto = blog.asSocialDto(frontendUrl)

            assertEquals(true, dto.url!!.contains("/blogs"))
            assertEquals(blog.title, dto.title)
            assertEquals(
                setOf(
                    PlatformType.FACEBOOK,
                    PlatformType.TWITTER,
                    PlatformType.INSTAGRAM,
                    PlatformType.LINKEDIN
                ),
                dto.platforms!!.toSet()
            )
        }
    }
}
