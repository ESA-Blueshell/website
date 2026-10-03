package net.blueshell.api.contribution.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class MemberContributionIT : UserTestSupport() {
    @Test
    fun `the board reads a person's contributions, and members cannot`() {
        val person = createUserWithRole(Role.MEMBER)

        mvc
            .perform(get("/users/${person.id}/contributions").with(signedIn(createUserWithRole(Role.BOARD))))
            .andExpect(status().isOk)
        mvc
            .perform(get("/users/${person.id}/contributions").with(signedIn(person)))
            .andExpect(status().isForbidden)
    }
}
