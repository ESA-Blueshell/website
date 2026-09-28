package net.blueshell.api.blog.web

import net.blueshell.api.blog.domain.BlogUseCases
import net.blueshell.api.blog.persistence.Blog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant

class BlogControllerReadTest {
    private val useCases = mock<BlogUseCases>()
    private val controller =
        BlogController(useCases).also { ReflectionTestUtils.setField(it, "frontendUrl", "https://site.test") }

    private val blog =
        Blog(title = "News", html = "<p>Hi</p>", publishedAt = Instant.EPOCH).also {
            it.id = 7
            it.createdAt = Instant.EPOCH
            it.updatedAt = Instant.EPOCH
        }

    @Test
    fun `reads every blog post and one by id through the use cases`() {
        whenever(useCases.all()).thenReturn(listOf(blog))
        whenever(useCases.byId(7)).thenReturn(blog)

        assertThat(controller.findBlogs().map { it.url }).containsExactly("https://site.test/blogs/7")
        assertThat(controller.findBlogById(7).title).isEqualTo("News")
    }

    @Test
    fun `removes a blog post through the use cases`() {
        controller.deleteById(7)

        verify(useCases).remove(7)
    }
}
