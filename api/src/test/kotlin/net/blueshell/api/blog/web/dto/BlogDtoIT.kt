package net.blueshell.api.blog.web.dto

import net.blueshell.api.blog.application.BlogService
import net.blueshell.api.blog.persistence.Blog
import net.blueshell.api.blog.web.mapping.asEntity
import net.blueshell.api.factory.dto.BlogDTOFactory
import net.blueshell.api.factory.model.BlogFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BlogDtoIT @Autowired constructor(
    private val blogDTOFactory: BlogDTOFactory,
    private val blogFactory: BlogFactory,
    private val blogService: BlogService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists sanitized html`() {
            val dto = blogDTOFactory.createBasic().apply {
                html = "<div><a>Unsubscribe</a></div><p>Keep</p>"
            }
            val blog = blogFactory.createBasic()

            val mapped = dto.asEntity(blog)
            val saved = blogService.create(mapped)
            flushAndClear()

            val reloaded = reload(Blog::class.java, saved.id!!)

            assertThat(reloaded.title).isEqualTo(dto.title)
            assertThat(reloaded.html).contains("Keep")
            assertThat(reloaded.html).doesNotContain("Unsubscribe")
        }
    }
}
