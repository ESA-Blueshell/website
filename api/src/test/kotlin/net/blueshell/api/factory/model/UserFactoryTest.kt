package net.blueshell.api.factory.model

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable user`() {
        val user = userFactory.createBasic()
        val saved = persist(user)
        assertPersisted(User::class.java, saved.id)
    }

    @Test
    fun `creates user with specific role`() {
        val user = userFactory.createWithRole(Role.ADMIN)
        val saved = persist(user)
        assertPersisted(User::class.java, saved.id)
        assertEquals(setOf(Role.ADMIN), saved.roles)
    }

    @Test
    fun `creates admin user`() {
        val user = userFactory.createAdmin()
        val saved = persist(user)
        assertPersisted(User::class.java, saved.id)
        assertEquals(setOf(Role.ADMIN), saved.roles)
    }

    @Test
    fun `creates board member user`() {
        val user = userFactory.createBoardMember()
        val saved = persist(user)
        assertPersisted(User::class.java, saved.id)
        assertEquals(setOf(Role.BOARD), saved.roles)
    }

    @Test
    fun `creates committee member user`() {
        val user = userFactory.createCommitteeMember()
        val saved = persist(user)
        assertPersisted(User::class.java, saved.id)
        assertEquals(setOf(Role.COMMITTEE), saved.roles)
    }
}
