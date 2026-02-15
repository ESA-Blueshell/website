package net.blueshell.api.domain.auth.web

import net.blueshell.api.domain.auth.application.factory.RecoveryTokenFactory
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@SpringBootTest
class RecoveryControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var recoveryTokenFactory: RecoveryTokenFactory

    @Test
    fun `sets password with recovery token`() {
        val user = createUserWithRole(Role.MEMBER)
        val token = recoveryTokenFactory.issue(user, ResetType.PASSWORD_RESET, Duration.ofMinutes(30))
        val newPassword = "NewPassword123!"

        mvc.perform(
            post("/recovery/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token","password":"$newPassword"}""")
        )
            .andExpect(status().isNoContent)

        val refreshed = userRepository.findById(user.id!!).orElseThrow()
        assertThat(passwordEncoder.matches(newPassword, refreshed.password)).isTrue()
    }

    @Test
    fun `activates user with activation token`() {
        val user = createUserWithRole(Role.MEMBER, enabled = false)
        val token = recoveryTokenFactory.issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))

        mvc.perform(
            post("/recovery/user/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token"}""")
        )
            .andExpect(status().isOk)

        assertThat(userRepository.findById(user.id!!).orElseThrow().enabled).isTrue()
    }

    @Test
    fun `activates member with username and password`() {
        val user = createUserWithRole(Role.MEMBER, enabled = false)
        val token = recoveryTokenFactory.issue(user, ResetType.MEMBER_ACTIVATION, Duration.ofDays(7))
        val username = "activated_${System.currentTimeMillis()}"
        val password = "ChangedPass123!"

        mvc.perform(
            post("/recovery/member/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token","username":"$username","password":"$password"}""")
        )
            .andExpect(status().isOk)

        val refreshed = userRepository.findById(user.id!!).orElseThrow()
        assertThat(refreshed.enabled).isTrue()
        assertThat(refreshed.username).isEqualTo(username)
        assertThat(passwordEncoder.matches(password, refreshed.password)).isTrue()
    }
}
