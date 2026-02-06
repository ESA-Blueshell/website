package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class UserFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable user`() {
        val user = userFactory.createBasic()
        val saved = persist(user)
        assertPersisted(net.blueshell.api.model.User::class.java, saved.id)
    }
}
