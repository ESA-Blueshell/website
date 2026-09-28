package net.blueshell.api.blog.domain

import net.blueshell.api.blog.persistence.Blog
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.OptimisticLockingFailureException
import java.time.Instant

class BlogUseCasesTest {
    private val blogService = mock<BlogService>()
    private val useCases = BlogUseCases(blogService)

    @Nested
    inner class Create {
        @Test
        fun `creates blog and sanitizes html`() {
            val captured = argumentCaptor<Blog>()
            whenever(blogService.create(captured.capture())).thenAnswer { captured.firstValue }
            val publishedAt = Instant.parse("2025-01-01T00:00:00Z")

            val result =
                useCases.create(
                    title = "Blog title",
                    html =
                        """
                        <div><a>Unsubscribe</a></div>
                        <p>Hello world</p>
                        <script>alert('xss')</script>
                        <img src="https://example.com/image.png" onerror="alert('xss')" />
                        """.trimIndent(),
                    publishedAt = publishedAt,
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
    inner class Update {
        @Test
        fun `updates blog fields, keeping the version it was read at`() {
            val existing =
                Blog(
                    title = "Old",
                    html = "<p>Old</p>",
                    publishedAt = Instant.parse("2024-01-01T00:00:00Z"),
                ).apply { version = 1L }
            whenever(blogService.findById(11L)).thenReturn(existing)
            whenever(blogService.update(existing)).thenReturn(existing)
            val newPublishedAt = Instant.parse("2025-06-01T00:00:00Z")

            val result =
                useCases.update(
                    id = 11L,
                    title = "New",
                    html =
                        """
                        <div><a>Unsubscribe</a></div>
                        <p>New</p>
                        <a href="javascript:alert('xss')">Click me</a>
                        """.trimIndent(),
                    publishedAt = newPublishedAt,
                    version = 1L,
                )

            assertThat(existing.title).isEqualTo("New")
            assertThat(existing.html).doesNotContain("Unsubscribe")
            assertThat(existing.html).doesNotContain("javascript:")
            assertThat(existing.publishedAt).isEqualTo(newPublishedAt)
            assertThat(existing.version).isEqualTo(1L)
            assertThat(result).isSameAs(existing)
        }

        @Test
        fun `refuses an edit made against an older version, before touching a field`() {
            val existing =
                Blog(title = "Old", html = "<p>Old</p>", publishedAt = Instant.parse("2024-01-01T00:00:00Z")).apply { version = 2L }
            whenever(blogService.findById(11L)).thenReturn(existing)

            assertThatThrownBy { useCases.update(11L, "New", "<p>New</p>", Instant.parse("2025-06-01T00:00:00Z"), version = 1L) }
                .isInstanceOf(OptimisticLockingFailureException::class.java)
            assertThat(existing.title).isEqualTo("Old")
            verify(blogService, never()).update(any())
        }
    }
}
