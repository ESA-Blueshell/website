package net.blueshell.api.auth.web

import net.blueshell.api.auth.domain.RecoveryTokenFactory
import net.blueshell.api.factory.auth.web.request.AuthRequestFactory
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.util.stream.Stream

@SpringBootTest
class RecoveryControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var recoveryTokenFactory: RecoveryTokenFactory

    @Autowired
    private lateinit var authRequestFactory: AuthRequestFactory

    @Nested
    inner class ResetPassword {
        @Test
        fun `requests password reset and schedules recovery email`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(post("/recovery/password/reset/{username}", user.username))
                .andExpect(status().isNoContent)

            val jobs = findJobsByType(EmailJobs.Recovery.type)
            assertThat(jobs)
                .hasSize(1)
                .anySatisfy {
                    assertThat(it.payload).contains("\"userId\":${user.id}")
                    assertThat(it.payload).contains("\"tokenPurpose\":\"PASSWORD_RESET\"")
                }
        }

        @Test
        fun `returns no content for unknown username`() {
            mvc.perform(post("/recovery/password/reset/{username}", "missing_${System.currentTimeMillis()}"))
                .andExpect(status().isNoContent)
        }
    }

    @Nested
    inner class SetPassword {
        @Test
        fun `sets password with recovery token`() {
            val user = createUserWithRole(Role.MEMBER)
            val token = recoveryTokenFactory.issue(user, TokenPurpose.PASSWORD_RESET, Duration.ofMinutes(30))
            val newPassword = "NewPassword123!"

            mvc.perform(
                post("/recovery/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.passwordResetPayload(token, newPassword))
            )
                .andExpect(status().isNoContent)

            val refreshed = userRepository.findById(user.id!!).orElseThrow()
            assertThat(passwordEncoder.matches(newPassword, refreshed.password)).isTrue()
        }

        @Test
        fun `returns bad request for invalid password format`() {
            val user = createUserWithRole(Role.MEMBER)
            val token = recoveryTokenFactory.issue(user, TokenPurpose.PASSWORD_RESET, Duration.ofMinutes(30))
            val weakPassword = "WeakPass12"

            val result = mvc.perform(
                post("/recovery/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.passwordResetPayload(token, weakPassword))
            )
                .andExpect(status().isBadRequest)
                .andReturn()

            assertThat(result.response.contentAsString)
                .doesNotContain("\"rejectedValue\"")
                .doesNotContain(weakPassword)
        }

        @ParameterizedTest(name = "invalid password: {0}")
        @MethodSource("net.blueshell.api.auth.web.RecoveryControllerIT#invalidPasswords")
        fun `validates various invalid passwords`(invalidPassword: String) {
            val user = createUserWithRole(Role.MEMBER)
            val token = recoveryTokenFactory.issue(user, TokenPurpose.PASSWORD_RESET, Duration.ofMinutes(30))

            val result = mvc.perform(
                post("/recovery/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.passwordResetPayload(token, invalidPassword))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty)
                .andReturn()

            assertThat(result.response.contentAsString).doesNotContain(invalidPassword)
        }
    }

    @Nested
    inner class UserActivate {
        @Test
        fun `activates an account that has no application to complete`() {
            val user = createUserWithRole(Role.MEMBER, enabled = false)
            val token = recoveryTokenFactory.issue(user, TokenPurpose.USER_ACTIVATION, Duration.ofHours(1))

            mvc.perform(
                post("/recovery/user/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.userActivationPayload(token))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.membershipStarted").value(false))

            assertThat(userRepository.findById(user.id!!).orElseThrow().enabled).isTrue()
        }

        @Test
        fun `activating does not start a membership while the application is incomplete`() {
            val user = assignMemberProfile(createUserWithRole(Role.MEMBER, enabled = false))
            val token = recoveryTokenFactory.issue(user, TokenPurpose.USER_ACTIVATION, Duration.ofHours(1))

            mvc.perform(
                post("/recovery/user/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.userActivationPayload(token))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.membershipStarted").value(false))

            assertThat(userRepository.findById(user.id!!).orElseThrow().enabled).isTrue()
        }
    }

    @Nested
    inner class MemberActivate {
        @Test
        fun `activates member with username and password`() {
            val user = createUserWithRole(Role.MEMBER, enabled = false)
            val token = recoveryTokenFactory.issue(user, TokenPurpose.MEMBER_ACTIVATION, Duration.ofDays(7))
            val username = "activated_${System.currentTimeMillis()}"
            val password = "ChangedPass123!"

            mvc.perform(
                post("/recovery/member/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.memberActivationPayload(token, username, password))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.path").value("/"))

            val refreshed = userRepository.findById(user.id!!).orElseThrow()
            assertThat(refreshed.enabled).isTrue()
            assertThat(refreshed.username).isEqualTo(username)
            assertThat(passwordEncoder.matches(password, refreshed.password)).isTrue()
        }
    }

    @Nested
    inner class ResendUserActivation {
        @Test
        fun `resends user activation email for disabled user`() {
            val user = createUserWithRole(Role.MEMBER, enabled = false)

            mvc.perform(post("/recovery/user/activate/resend/{username}", user.username))
                .andExpect(status().isNoContent)

            val jobs = findJobsByType(EmailJobs.Recovery.type)
            assertThat(jobs)
                .hasSize(1)
                .anySatisfy {
                    assertThat(it.payload).contains("\"userId\":${user.id}")
                    assertThat(it.payload).contains("\"tokenPurpose\":\"USER_ACTIVATION\"")
                }
        }
    }

    @Nested
    inner class ResendMemberActivationEmail {
        @Test
        fun `board resends member activation email by user id`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER, enabled = false)
            recoveryTokenFactory.issue(user, TokenPurpose.MEMBER_ACTIVATION, Duration.ofDays(7))

            mvc.perform(
                post("/recovery/users/{userId}/resend/recovery", user.id)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)

            val jobs = findJobsByType(EmailJobs.Recovery.type)
            assertThat(jobs)
                .hasSize(1)
                .anySatisfy {
                    assertThat(it.payload).contains("\"userId\":${user.id}")
                    assertThat(it.payload).contains("\"tokenPurpose\":\"MEMBER_ACTIVATION\"")
                }
        }
    }

    companion object {
        @JvmStatic
        fun invalidPasswords(): Stream<String> = Stream.of(
            "Ab1!",                          // too short (4 chars)
            "ABCDEF1!",                      // no lowercase
            "abcdef1!",                      // no uppercase
            "Abcdef!!",                      // no digit
            "Abcdef12",                      // no special char
            "Abcdef1!" + "x".repeat(93),     // too long (101 chars)
        )
    }
}
