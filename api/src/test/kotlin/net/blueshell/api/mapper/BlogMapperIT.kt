package net.blueshell.api.mapper

import net.blueshell.api.factory.dto.BlogDTOFactory
import net.blueshell.api.factory.model.BlogFactory
import net.blueshell.api.model.Blog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BlogMapperIT @Autowired constructor(
    private val blogMapper: BlogMapper,
    private val blogDTOFactory: BlogDTOFactory,
    private val blogFactory: BlogFactory
) : MapperTestSupport() {
    @Test
    fun `persists sanitized html and url`() {
        val dto = blogDTOFactory.createBasic().apply {
            html = "<div><a>Unsubscribe</a></div><p>Keep</p>"
        }
        val blog = blogFactory.createBasic()

        val mapped = blogMapper.fromDTO(dto, blog)
        val saved = persist(mapped)
        flushAndClear()

        val reloaded = reload(Blog::class.java, saved.id!!)
        val mappedDto = blogMapper.toDTO(reloaded)

        assertThat(reloaded.title).isEqualTo(dto.title)
        assertThat(reloaded.html).contains("Keep")
        assertThat(reloaded.html).doesNotContain("Unsubscribe")
        assertThat(mappedDto.url).contains("/blogs/${reloaded.id}")
    }
}
