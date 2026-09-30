package net.blueshell.api.contribution.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class PeriodStandingIT : UserTestSupport() {
    @Test
    fun `the board reads how the current period stands, and members cannot`() {
        mvc
            .perform(get("/contributionPeriods/current/standing").with(signedIn(createUserWithRole(Role.BOARD))))
            .andExpect(status().is2xxSuccessful)
        mvc
            .perform(get("/contributionPeriods/current/standing").with(signedIn(createUserWithRole(Role.MEMBER))))
            .andExpect(status().isForbidden)
    }
}
