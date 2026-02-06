package net.blueshell.api.integration.model

import net.blueshell.api.model.Blog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BlogModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_column_fields() {
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
}
