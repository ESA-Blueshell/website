package net.blueshell.api.oidc.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MyServicesControllerTest {
    private val controller = MyServicesController()

    private fun principal(vararg roles: Role) = UserPrincipal(1, "u", "h", true, roles.toSet(), null, null)

    private fun idsFor(principal: UserPrincipal?) = controller.myServices(principal).body!!.map { it.id }

    @Test
    fun `a member is offered only the public status page`() {
        val ids = idsFor(principal(Role.MEMBER))
        assertTrue(ids.contains("status"))
        assertFalse(ids.contains("vault"))
        assertFalse(ids.contains("stalwart"))
    }

    @Test
    fun `an admin is offered the admin tools`() {
        assertTrue(idsFor(principal(Role.ADMIN)).contains("vault"))
    }

    @Test
    fun `a signed-out visitor is offered only the public status page`() {
        assertEquals(listOf("status"), idsFor(null))
    }
}
