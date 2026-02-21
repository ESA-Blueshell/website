package net.blueshell.api.domain.blog.web.mapping.response

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BlogResponseMappingsTest {

    @Test
    fun `sanitize removes script tags`() {
        val html = "<p>Safe</p><script>alert('xss')</script>"

        val sanitized = sanitizeBlogHtml(html)

        assertThat(sanitized).contains("<p>Safe</p>")
        assertThat(sanitized).doesNotContain("<script")
        assertThat(sanitized).doesNotContain("alert('xss')")
    }

    @Test
    fun `sanitize strips inline handlers and javascript links`() {
        val html = """
            <img src="https://example.com/image.png" onerror="alert('xss')" />
            <a href="javascript:alert('xss')">Click me</a>
        """.trimIndent()

        val sanitized = sanitizeBlogHtml(html)

        assertThat(sanitized).contains("<img")
        assertThat(sanitized).doesNotContain("onerror")
        assertThat(sanitized).doesNotContain("javascript:")
    }

    @Test
    fun `sanitize removes unsubscribe block`() {
        val html = """
            <div><a href="https://example.com/unsubscribe">Unsubscribe</a></div>
            <p>Body</p>
        """.trimIndent()

        val sanitized = sanitizeBlogHtml(html)

        assertThat(sanitized).contains("<p>Body</p>")
        assertThat(sanitized).doesNotContain("Unsubscribe")
    }

    @Test
    fun `sanitize returns empty string for blank input`() {
        assertThat(sanitizeBlogHtml("   ")).isEmpty()
    }
}
