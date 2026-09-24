package net.blueshell.api.auth

import net.blueshell.api.auth.domain.BreakGlass
import net.blueshell.api.auth.domain.BreakGlassAction
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TokenPurpose
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class TwoFactorResetIT : AccountSecurityTestSupport() {
    @Autowired
    private lateinit var breakGlass: BreakGlass

    private fun reset(
        userId: Long,
        by: RequestPostProcessor,
    ) = mvc.perform(json(post("/users/{id}/two-factor/reset", userId), """{"reason":"lost phone and codes"}""").with(by))

    private fun reenrol(
        username: String,
        token: String,
    ) = mvc.perform(
        json(post("/recovery/two-factor/re-enrol"), """{"token":"$token","username":"$username","password":"Password123!"}"""),
    )

    @Test
    fun `an admin resets somebody's two-factor with a step-up, never their own`() {
        val admin = createUserWithRole(Role.ADMIN)
        val member = createUserWithRole(Role.MEMBER)
        enrol(member)

        reset(member.id!!, signedIn(admin)).andExpect(jsonPath("$.code").value("StepUpRequired"))
        reset(admin.id!!, signedIn(admin, steppedUp = true)).andExpect(jsonPath("$.code").value("OwnAccount"))
        reset(member.id!!, signedIn(createUserWithRole(Role.BOARD), steppedUp = true)).andExpect(status().isForbidden)
        reset(member.id!!, signedIn(admin, steppedUp = true)).andExpect(status().isNoContent)

        mvc
            .perform(get("/users/{id}/account-security", member.id).with(signedIn(admin)))
            .andExpect(jsonPath("$.twoFactorOn").value(false))
            .andExpect(jsonPath("$.awaitingReenrolment").value(true))
    }

    @Test
    fun `after a reset every sign-in is ended and the password alone does not get back in`() {
        val admin = createUserWithRole(Role.ADMIN)
        val member = createUserWithRole(Role.MEMBER)
        enrol(member)
        val elsewhere = signedIn(member)

        reset(member.id!!, signedIn(admin, steppedUp = true))

        mvc.perform(get("/users/${member.id}").with(elsewhere)).andExpect(status().isUnauthorized)
        passwordStep(member).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("ReenrolmentRequired"))
    }

    @Test
    fun `the password and the link sign in once, and a resend retires the link before it`() {
        val admin = createUserWithRole(Role.ADMIN)
        val member = createUserWithRole(Role.MEMBER)
        enrol(member)
        reset(member.id!!, signedIn(admin, steppedUp = true))
        val first = recoveryLink(member.id!!, TokenPurpose.TWO_FACTOR_REENROLMENT)

        mvc.perform(post("/users/{id}/two-factor/reset/resend", member.id).with(signedIn(admin))).andExpect(status().isNoContent)
        val second = recoveryLink(member.id!!, TokenPurpose.TWO_FACTOR_REENROLMENT)

        reenrol(member.username, first).andExpect(status().isNotFound)
        reenrol(member.username, second)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.login.twoFactor.on").value(false))
            .andExpect(jsonPath("$.login.twoFactor.required").value(false))
        reenrol(member.username, second).andExpect(status().isBadRequest)
        passwordStep(member).andExpect(jsonPath("$.status").value("SIGNED_IN"))
        mvc
            .perform(post("/users/{id}/two-factor/reset/resend", member.id).with(signedIn(admin)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("NotAwaitingReenrolment"))
    }

    @Test
    fun `a reset board member is sent to set up again, a reset member is not`() {
        val admin = createUserWithRole(Role.ADMIN)
        val board = createUserWithRole(Role.BOARD)
        enrol(board)
        reset(board.id!!, signedIn(admin, steppedUp = true))

        reenrol(board.username, recoveryLink(board.id!!, TokenPurpose.TWO_FACTOR_REENROLMENT))
            .andExpect(jsonPath("$.login.twoFactor.required").value(true))
            .andExpect(jsonPath("$.login.roles").value(not(hasItem("BOARD"))))
    }

    @Test
    fun `the break-glass command resets or unlocks for the operator and tells every admin`() {
        createUserWithRole(Role.ADMIN)
        val lastAdmin = createUserWithRole(Role.ADMIN)
        enrol(lastAdmin)

        breakGlass.run(BreakGlassAction.RESET_TWO_FACTOR, lastAdmin.username, "lost everything")

        assertThat(refreshUser(lastAdmin).awaitingReenrolment).isTrue()
        assertThat(notices("ADMINISTRATOR")).hasSize(2)
    }
}
