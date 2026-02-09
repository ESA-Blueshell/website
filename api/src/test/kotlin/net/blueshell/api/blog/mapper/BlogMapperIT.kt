package net.blueshell.api.blog.mapper

import net.blueshell.api.factory.dto.BlogDTOFactory
import net.blueshell.api.factory.model.BlogFactory
import net.blueshell.api.blog.mapper.BlogMapper
import net.blueshell.api.blog.model.Blog
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BlogMapperIT @Autowired constructor(
    private val blogMapper: BlogMapper,
    private val blogDTOFactory: BlogDTOFactory,
    private val blogFactory: BlogFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted blog`() {
            val blog = persist(blogFactory.createBasic())

            val dto = blogMapper.toDTO(blog)

            assertThat(dto.id).isEqualTo(blog.id)
            assertThat(dto.title).isEqualTo(blog.title)
            assertThat(dto.html).isEqualTo(blog.html)
            assertThat(dto.publishedAt).isEqualTo(blog.publishedAt)
            assertThat(dto.url).contains("/blogs/${blog.id}")
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists sanitized html`() {
            val dto = blogDTOFactory.createBasic().apply {
                html = "<div><a>Unsubscribe</a></div><p>Keep</p>"
            }
            val blog = blogFactory.createBasic()

            val mapped = blogMapper.fromDTO(dto, blog)
            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(Blog::class.java, saved.id!!)

            assertThat(reloaded.title).isEqualTo(dto.title)
            assertThat(reloaded.html).contains("Keep")
            assertThat(reloaded.html).doesNotContain("Unsubscribe")
        }
    }
}
