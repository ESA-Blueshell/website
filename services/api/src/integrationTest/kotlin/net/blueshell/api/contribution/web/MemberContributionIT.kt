package net.blueshell.api.contribution.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class MemberContributionIT : UserTestSupport() {
    @Test
    fun `a member reads only their own contributions, and nobody signed out reads any`() {
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(user = member)
        val period = createContributionPeriodFixture()
        val stranger = createUserWithRole(Role.GUEST)

        mvc
            .perform(get("/users/me/contributions").with(signedIn(member)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.held").value(true))
            .andExpect(jsonPath("$.periods[0].periodId").value(period.id!!))
        mvc
            .perform(get("/users/me/contributions").with(signedIn(stranger)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.held").value(false))
            .andExpect(jsonPath("$.periods").isEmpty)
        mvc.perform(get("/users/me/contributions")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a signed-in person reads the account to transfer to`() {
        mvc
            .perform(get("/contributions/bank-account").with(signedIn(createUserWithRole(Role.GUEST))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.iban").isNotEmpty)
        mvc.perform(get("/contributions/bank-account")).andExpect(status().isUnauthorized)
    }
}
