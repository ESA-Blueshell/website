package net.blueshell.api.domain.blog.application.command

import net.blueshell.api.domain.blog.application.BlogService
import net.blueshell.api.domain.blog.command.CreateBlogCommand
import net.blueshell.api.domain.blog.command.DeleteBlogByIdCommand
import net.blueshell.api.domain.blog.command.FindBlogByIdCommand
import net.blueshell.api.domain.blog.command.FindBlogsCommand
import net.blueshell.api.domain.blog.command.UpdateBlogCommand
import net.blueshell.api.domain.blog.persistence.Blog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class BlogCommandHandlersTest {

    private val blogService = mock<BlogService>()

    @Nested
    inner class CreateBlog {

        private val handler = CreateBlogHandler(blogService)

        @Test
        fun `creates blog and sanitizes html`() {
            val captured = argumentCaptor<Blog>()
            whenever(blogService.create(captured.capture())).thenAnswer { captured.firstValue }
            val publishedAt = Instant.parse("2025-01-01T00:00:00Z")

            val result = handler.handle(
                CreateBlogCommand(
                    title = "Blog title",
                    html = """
                        <div><a>Unsubscribe</a></div>
                        <p>Hello world</p>
                        <script>alert('xss')</script>
                        <img src="https://example.com/image.png" onerror="alert('xss')" />
                    """.trimIndent(),
                    publishedAt = publishedAt
                )
            )

            assertThat(captured.firstValue.title).isEqualTo("Blog title")
            assertThat(captured.firstValue.html).doesNotContain("Unsubscribe")
            assertThat(captured.firstValue.html).doesNotContain("<script")
            assertThat(captured.firstValue.html).doesNotContain("onerror")
            assertThat(captured.firstValue.publishedAt).isEqualTo(publishedAt)
            assertThat(result.title).isEqualTo("Blog title")
            assertThat(result.html).doesNotContain("Unsubscribe")
            assertThat(result.html).doesNotContain("<script")
            assertThat(result.html).doesNotContain("onerror")
        }
    }

    @Nested
    inner class UpdateBlog {

        private val handler = UpdateBlogHandler(blogService)

        @Test
        fun `updates blog fields and version`() {
            val existing = blog("Old", "<p>Old</p>", Instant.parse("2024-01-01T00:00:00Z")).apply { version = 1L }
            whenever(blogService.findById(11L)).thenReturn(existing)
            whenever(blogService.update(existing)).thenReturn(existing)
            val newPublishedAt = Instant.parse("2025-06-01T00:00:00Z")

            val result = handler.handle(
                UpdateBlogCommand(
                    id = 11L,
                    title = "New",
                    html = """
                        <div><a>Unsubscribe</a></div>
                        <p>New</p>
                        <a href="javascript:alert('xss')">Click me</a>
                    """.trimIndent(),
                    publishedAt = newPublishedAt,
                    version = 4L
                )
            )

            assertThat(existing.title).isEqualTo("New")
            assertThat(existing.html).doesNotContain("Unsubscribe")
            assertThat(existing.html).doesNotContain("javascript:")
            assertThat(existing.publishedAt).isEqualTo(newPublishedAt)
            assertThat(existing.version).isEqualTo(4L)
            assertThat(result).isSameAs(existing)
        }
    }

    @Nested
    inner class FindBlogs {

        private val handler = FindBlogsHandler(blogService)

        @Test
        fun `returns all blogs`() {
            val blogs = mutableListOf(
                blog("A", "<p>A</p>", Instant.parse("2025-01-01T00:00:00Z")),
                blog("B", "<p>B</p>", Instant.parse("2025-01-02T00:00:00Z"))
            )
            whenever(blogService.findAll()).thenReturn(blogs)

            val result = handler.handle(FindBlogsCommand())

            assertThat(result).isSameAs(blogs)
            verify(blogService).findAll()
        }
    }

    @Nested
    inner class FindBlogById {

        private val handler = FindBlogByIdHandler(blogService)

        @Test
        fun `returns blog by id`() {
            val expected = blog("Blog", "<p>Body</p>", Instant.parse("2025-01-03T00:00:00Z"))
            whenever(blogService.findById(15L)).thenReturn(expected)

            val result = handler.handle(FindBlogByIdCommand(15L))

            assertThat(result).isSameAs(expected)
            verify(blogService).findById(15L)
        }
    }

    @Nested
    inner class DeleteBlogById {

        private val handler = DeleteBlogByIdHandler(blogService)

        @Test
        fun `deletes blog by id`() {
            handler.handle(DeleteBlogByIdCommand(20L))

            verify(blogService).deleteById(eq(20L))
        }
    }

    private fun blog(title: String, html: String, publishedAt: Instant): Blog =
        Blog(title = title, html = html, publishedAt = publishedAt)
}
