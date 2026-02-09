package net.blueshell.api.auth.web

import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.factory.dto.request.MemberActivationRequestFactory
import net.blueshell.api.factory.dto.request.PasswordResetRequestFactory
import net.blueshell.api.factory.dto.request.UserActivationRequestFactory
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.user.persistence.User
import net.blueshell.api.auth.application.RecoveryService
import net.blueshell.api.platform.integration.mock.MockJavaMailSender
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
    private val passwordResetRequestFactory: PasswordResetRequestFactory,
    private val userActivationRequestFactory: UserActivationRequestFactory,
    private val memberActivationRequestFactory: MemberActivationRequestFactory
) : UserTestSupport() {

    @Autowired
    private lateinit var mailSender: MockJavaMailSender

    @BeforeEach
    fun clearOutbox() {
        mailSender.clear()
    }

    @Test
    fun `requests password reset by username`() {
        val user = userRepository.save(userFactory.createBasic())

        mvc.perform(post("/recovery/password/reset/{username}", user.username))
            .andExpect(status().isNoContent())

        assertLastEmail(
            toEmail = user.email,
            subject = "Reset Your Blueshell Account Password",
            bodyContains = "/account/reset-password?username="
        )
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
        assertThat(refreshed.enabled)
            .`as`("User should be enabled after member activation.")
            .isTrue()
        assertThat(refreshed.username)
            .`as`(
                "Username should be updated during member activation. expected=%s actual=%s",
                payload.username,
                refreshed.username
            )
            .isEqualTo(payload.username)
        assertThat(passwordEncoder.matches(payload.password, refreshed.password))
            .`as`("Password should be updated during member activation.")
            .isTrue()
    }

    @Test
    fun `resends user activation token by username`() {
        val user = userRepository.save(disabledUser(dateOfBirth = null))

        mvc.perform(post("/recovery/user/activate/resend/{username}", user.username))
            .andExpect(status().isNoContent())

        assertLastEmail(
            toEmail = user.email,
            subject = "Activate your Account",
            bodyContains = "/account/activate/user?username="
        )
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

        assertLastEmail(
            toEmail = user.email,
            subject = "Activate your Account",
            bodyContains = "/account/activate/member?token="
        )
    }

    private fun disabledUser(dateOfBirth: Date?): User {
        return userFactory.createBasic().apply {
            enabled = false
            this.dateOfBirth = dateOfBirth
        }
    }

    private fun assertLastEmail(toEmail: String, subject: String, bodyContains: String) {
        val message = awaitEmail(toEmail, subject, bodyContains)
        val recipients = (message.allRecipients ?: emptyArray()).map { it.toString() }
        val body = messageBody(message)

        assertThat(recipients)
            .`as`("Email recipients mismatch. expected=%s actual=%s", toEmail, recipients)
            .contains(toEmail)
        assertThat(message.subject)
            .`as`("Email subject mismatch. expected=%s actual=%s", subject, message.subject)
            .isEqualTo(subject)
        assertThat(body)
            .`as`("Email body missing expected fragment. expected=%s actualSnippet=%s", bodyContains, bodySnippet(body))
            .contains(bodyContains)
    }

    private fun bodySnippet(body: String, maxLen: Int = 400): String {
        val trimmed = body.replace("\n", " ").replace("\r", " ").trim()
        return if (trimmed.length <= maxLen) trimmed else trimmed.substring(0, maxLen) + "..."
    }

    private fun awaitEmail(
        toEmail: String,
        subject: String,
        bodyContains: String,
        timeoutMs: Long = 2000,
        pollMs: Long = 50
    ): MimeMessage {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val message = findMatchingEmail(toEmail, subject, bodyContains)
            if (message != null) return message
            Thread.sleep(pollMs)
        }
        val message = findMatchingEmail(toEmail, subject, bodyContains)
        checkNotNull(message) { "Expected email to be sent within ${timeoutMs}ms." }
        return message
    }

    private fun findMatchingEmail(toEmail: String, subject: String, bodyContains: String): MimeMessage? {
        return mailSender.outbox.firstOrNull { message ->
            val recipients = (message.allRecipients ?: emptyArray()).map { it.toString() }
            val body = messageBody(message)
            recipients.contains(toEmail) && message.subject == subject && body.contains(bodyContains)
        }
    }

    private fun messageBody(message: MimeMessage): String {
        return when (val content = message.content) {
            is String -> content
            is Multipart -> extractFromMultipart(content)
            else -> content.toString()
        }
    }

    private fun extractFromMultipart(multipart: Multipart): String {
        for (i in 0 until multipart.count) {
            val part = multipart.getBodyPart(i)
            val content = extractFromPart(part)
            if (content != null) return content
        }
        return ""
    }

    private fun extractFromPart(part: Part): String? {
        return when (val content = part.content) {
            is String -> content
            is Multipart -> extractFromMultipart(content)
            else -> null
        }
    }
}
