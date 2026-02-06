package net.blueshell.api.factory.model

import net.blueshell.api.model.User
import org.junit.jupiter.api.Test

class UserFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable user`() {
        val user = userFactory.createBasic()
        val saved = persist(user)
        assertPersisted(User::class.java, saved.id)
    }
}
