package net.blueshell.api.domain.blog.persistence

import net.blueshell.api.domain.blog.web.mapping.asDto
import net.blueshell.api.domain.blog.web.mapping.asSocialDto
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions
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

            Assertions.assertEquals(blog.title, found.title)
            Assertions.assertEquals(blog.html, found.html)
            Assertions.assertEquals(blog.publishedAt, found.publishedAt)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted blog`() {
            val blog = persist(blogFactory.createBasic())

            val dto = blog.asDto(frontendUrl)

            Assertions.assertEquals(blog.id, dto.id)
            Assertions.assertEquals(blog.title, dto.title)
            Assertions.assertEquals(blog.html, dto.html)
            Assertions.assertEquals(blog.publishedAt, dto.publishedAt)
            Assertions.assertEquals(true, dto.url!!.contains("/blogs/${blog.id}"))
        }

        @Test
        fun `builds social dto from blog`() {
            val blog = persist(blogFactory.createBasic())

            val dto = blog.asSocialDto(frontendUrl)

            Assertions.assertEquals(true, dto.url!!.contains("/blogs"))
            Assertions.assertEquals(blog.title, dto.title)
            Assertions.assertEquals(
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