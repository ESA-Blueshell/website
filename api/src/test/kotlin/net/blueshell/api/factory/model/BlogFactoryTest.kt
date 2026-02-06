package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class BlogFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable blog`() {
        val blog = blogFactory.createBasic()
        val saved = persist(blog)
        assertPersisted(net.blueshell.api.model.Blog::class.java, saved.id)
    }
}
