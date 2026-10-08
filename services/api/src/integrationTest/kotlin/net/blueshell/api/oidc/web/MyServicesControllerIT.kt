package net.blueshell.api.oidc.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class MyServicesControllerIT : UserTestSupport() {
    @Test
    fun `an admin is offered the admin tools`() {
        val admin = createUserWithRole(Role.ADMIN)
        mvc
            .perform(get("/me/services").with(signedIn(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].id", hasItem("vault")))
    }

    @Test
    fun `a member is offered the status page but not the admin tools`() {
        val member = createUserWithRole(Role.MEMBER)
        mvc
            .perform(get("/me/services").with(signedIn(member)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].id", hasItem("status")))
            .andExpect(jsonPath("$[*].id", not(hasItem("vault"))))
    }
}
