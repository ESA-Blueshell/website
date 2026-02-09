package net.blueshell.api.factory.model

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.feature.blog.model.Blog
import org.junit.jupiter.api.Test

class BlogFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable blog`() {
        val blog = blogFactory.createBasic()
        val saved = persist(blog)
        assertPersisted(Blog::class.java, saved.id)
    }
}
