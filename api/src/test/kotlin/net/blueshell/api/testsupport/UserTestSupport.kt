package net.blueshell.api.testsupport

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.infrastructure.security.JwtTokenGenerator
import net.blueshell.api.platform.integration.mock.MockJavaMailSender
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipalMapper
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.RequestPostProcessor

/**
 * Base class for controller integration tests involving users.
 *
 * Provides:
 * - MockMvc for HTTP testing
 * - User repository and password encoder
 * - JWT token generation for authentication
 * - Email mock for verification
 * - Helper methods for user management
 */
@AutoConfigureMockMvc
abstract class UserTestSupport : ServiceTestSupport() {

    @Autowired
    protected lateinit var mvc: MockMvc

    @Autowired
    protected lateinit var userRepository: UserRepository

    @Autowired
    protected lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    protected lateinit var tokenGenerator: JwtTokenGenerator

    @Autowired
    protected lateinit var mapper: ObjectMapper

    @Autowired
    protected lateinit var mailSender: MockJavaMailSender

    @Value("\${app.frontend-url}")
    protected lateinit var frontendUrl: String

    @Value("\${app.url}")
    protected lateinit var appUrl: String

    /**
     * Creates bearer token authentication for a user.
     */
    protected fun bearer(user: User): RequestPostProcessor {
        val principal = UserPrincipalMapper.fromUser(user)
        val token = tokenGenerator.generateToken(principal.username)
        return RequestPostProcessor { request ->
            request.addHeader("Authorization", "Bearer $token")
            request
        }
    }

    /**
     * Creates and persists a user with specific role.
     */
    protected fun createUserWithRole(role: Role, enabled: Boolean = true): User {
        val user = User(
            username = "user_${role.name.lowercase()}_${System.currentTimeMillis()}",
            password = passwordEncoder.encode("Password123!"),
            firstName = "Test",
            lastName = role.name
        )
        user.email = "${user.username}@test.com"
        user.roles = mutableSetOf(role)
        user.enabled = enabled
        return userRepository.save(user)
    }

    /**
     * Refreshes user from database.
     */
    protected fun refreshUser(user: User): User {
        entityManager.flush()
        entityManager.clear()
        return userRepository.findById(user.id!!).orElseThrow()
    }

    /**
     * Asserts that an email was sent with specific criteria.
     */
    protected fun assertEmailSent(
        toEmail: String,
        subject: String,
        bodyContains: String,
        timeoutMs: Long = 2000
    ) {
        val message = awaitEmail(toEmail, subject, bodyContains, timeoutMs)
        val recipients = (message.allRecipients ?: emptyArray()).map { it.toString() }
        val body = messageBody(message)

        assertThat(recipients)
            .describedAs("Email recipients should contain $toEmail")
            .contains(toEmail)
        assertThat(message.subject)
            .describedAs("Email subject should be: $subject")
            .isEqualTo(subject)
        assertThat(body)
            .describedAs("Email body should contain: $bodyContains")
            .contains(bodyContains)
    }

    /**
     * Waits for email matching criteria with timeout and polling.
     */
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
        checkNotNull(message) {
            "Expected email not found within ${timeoutMs}ms. " +
                    "to=$toEmail, subject=$subject, bodyContains=$bodyContains"
        }
        return message
    }

    /**
     * Finds email in outbox matching criteria.
     */
    private fun findMatchingEmail(toEmail: String, subject: String, bodyContains: String): MimeMessage? {
        return mailSender.outbox.firstOrNull { message ->
            val recipients = (message.allRecipients ?: emptyArray()).map { it.toString() }
            val body = messageBody(message)
            recipients.contains(toEmail) && message.subject == subject && body.contains(bodyContains)
        }
    }

    /**
     * Extracts text body from MIME message.
     */
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
