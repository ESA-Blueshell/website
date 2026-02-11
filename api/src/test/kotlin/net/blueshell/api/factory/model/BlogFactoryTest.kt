package net.blueshell.api.factory.model

import net.blueshell.api.domain.blog.persistence.Blog
import org.junit.jupiter.api.Test

class BlogFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable blog`() {
        val blog = blogFactory.createBasic()
        val saved = persist(blog)
        assertPersisted(Blog::class.java, saved.id)
    }
}
