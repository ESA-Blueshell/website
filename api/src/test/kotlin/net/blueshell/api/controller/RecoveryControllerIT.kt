package net.blueshell.api.controller

import net.blueshell.api.common.enums.ResetType
import net.blueshell.api.common.enums.Role
import net.blueshell.api.factory.dto.request.MemberActivationRequestFactory
import net.blueshell.api.factory.dto.request.PasswordResetRequestFactory
import net.blueshell.api.factory.dto.request.UserActivationRequestFactory
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.model.User
import net.blueshell.api.service.RecoveryService
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Date
import java.time.Duration
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
class RecoveryControllerIT @Autowired constructor(
    private val recoveryService: RecoveryService,
    private val userFactory: UserFactory,
    private val passwordEncoder: PasswordEncoder,
    private val passwordResetRequestFactory: PasswordResetRequestFactory,
    private val userActivationRequestFactory: UserActivationRequestFactory,
    private val memberActivationRequestFactory: MemberActivationRequestFactory
) : UserTestSupport() {

    @Test
    fun `requests password reset by username`() {
        val user = userRepository.save(userFactory.createBasic())

        mvc.perform(post("/recovery/password/reset/{username}", user.username))
            .andExpect(status().isNoContent())
    }

    @Test
    fun `sets password with recovery token`() {
        val user = userRepository.save(userFactory.createBasic())
        val rawToken = recoveryService.issue(user, ResetType.PASSWORD_RESET, Duration.ofMinutes(30))
        val newPassword = "NewPassword123!"

        val payload = passwordResetRequestFactory.createBasic().apply {
            token = rawToken
            password = newPassword
        }

        mvc.perform(
            post("/recovery/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(payload))
        )
            .andExpect(status().isNoContent())

        val refreshed = refreshUser(user)
        assertTrue(passwordEncoder.matches(newPassword, refreshed.password))
    }

    @Test
    fun `activates user without date of birth`() {
        val user = userRepository.save(disabledUser(dateOfBirth = null))
        val rawToken = recoveryService.issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))

        val payload = userActivationRequestFactory.createBasic().apply { token = rawToken }

        mvc.perform(
            post("/recovery/user/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(payload))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path").value("/"))

        assertTrue(refreshUser(user).enabled)
    }

    @Test
    fun `activates user with date of birth`() {
        val dateOfBirth = Date.valueOf(LocalDate.now().minusYears(20))
        val user = userRepository.save(disabledUser(dateOfBirth))
        val rawToken = recoveryService.issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))

        val payload = userActivationRequestFactory.createBasic().apply { token = rawToken }

        mvc.perform(
            post("/recovery/user/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(payload))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path").value("/membership/signUp?step=2"))

        assertTrue(refreshUser(user).enabled)
    }

    @Test
    fun `activates member with username and password`() {
        val user = userRepository.save(disabledUser(dateOfBirth = null))
        val rawToken = recoveryService.issue(user, ResetType.MEMBER_ACTIVATION, Duration.ofDays(7))

        val payload = memberActivationRequestFactory.createBasic().apply { token = rawToken }

        mvc.perform(
            post("/recovery/member/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(payload))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path").value("/"))

        val refreshed = refreshUser(user)
        assertAll(
            { assertTrue(refreshed.enabled) },
            { assertEquals(payload.username, refreshed.username) },
            { assertTrue(passwordEncoder.matches(payload.password, refreshed.password)) },
        )
    }

    @Test
    fun `resends user activation token by username`() {
        val user = userRepository.save(disabledUser(dateOfBirth = null))

        mvc.perform(post("/recovery/user/activate/resend/{username}", user.username))
            .andExpect(status().isNoContent())
    }

    @Test
    fun `resends activation email for user as board`() {
        val user = userRepository.save(disabledUser(dateOfBirth = null))
        val board = createUserWithRole(Role.BOARD)
        recoveryService.issue(user, ResetType.MEMBER_ACTIVATION, Duration.ofDays(7))

        mvc.perform(
            post("/recovery/users/{userId}/resend/recovery", user.id)
                .with(bearer(board))
        )
            .andExpect(status().isNoContent())
    }

    private fun disabledUser(dateOfBirth: Date?): User {
        return userFactory.createBasic().apply {
            enabled = false
            this.dateOfBirth = dateOfBirth
        }
    }
}
