package net.blueshell.api.auth.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class LastRecoveryEmailsIT : UserTestSupport() {
    @Test
    fun `the board reads when a password reset last went out, and members cannot`() {
        val person = createUserWithRole(Role.MEMBER)
        mvc.perform(post("/recovery/password/reset/${person.username}")).andExpect(status().isNoContent)

        mvc
            .perform(get("/recovery/last-emails").with(signedIn(createUserWithRole(Role.BOARD))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.emails[?(@.userId == ${person.id})].sentAt").isNotEmpty)
        mvc.perform(get("/recovery/last-emails").with(signedIn(person))).andExpect(status().isForbidden)
    }
}
