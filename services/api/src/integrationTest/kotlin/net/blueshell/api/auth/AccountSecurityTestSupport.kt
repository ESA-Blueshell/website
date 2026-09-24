package net.blueshell.api.auth

import jakarta.servlet.http.Cookie
import net.blueshell.api.auth.domain.twofactor.Base32
import net.blueshell.api.auth.persistence.RecoveryTokenRepository
import net.blueshell.api.auth.domain.twofactor.Totp
import net.blueshell.api.auth.web.AuthenticationController
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.time.Duration
import java.time.Instant

/** Signing in through the endpoints, setting up two-factor, and reading the links the emails carry. */
abstract class AccountSecurityTestSupport : UserTestSupport() {
    @Autowired
    protected lateinit var recoveryTokens: RecoveryTokenRepository

    @BeforeEach
    fun stopTheClock() {
        clock.set(Instant.parse("2026-09-24T12:00:00Z"))
    }

    protected fun json(
        builder: MockHttpServletRequestBuilder,
        body: String,
    ): MockHttpServletRequestBuilder = builder.contentType(MediaType.APPLICATION_JSON).content(body)

    protected fun passwordStep(
        user: User,
        password: String = "Password123!",
        vararg cookies: Cookie,
    ): ResultActions =
        mvc.perform(
            json(post("/auth"), """{"username":"${user.username}","password":"$password"}""").also {
                if (cookies.isNotEmpty()) it.cookie(*cookies)
            },
        )

    protected fun codeStep(
        challenge: Cookie,
        code: String,
        trustThisBrowser: Boolean = false,
    ): ResultActions =
        mvc.perform(json(post("/auth/two-factor"), """{"code":"$code","trustThisBrowser":$trustThisBrowser}""").cookie(challenge))

    protected fun MvcResult.cookie(name: String): Cookie? = response.cookies.firstOrNull { it.name == name && it.value.isNotBlank() }

    protected val MvcResult.authCookie: Cookie? get() = cookie("BSH_AUTH")

    protected val MvcResult.challengeCookie: Cookie get() = requireNotNull(cookie(AuthenticationController.CHALLENGE_COOKIE))

    /** Sets up an authenticator app the way the security page does, and answers its key. */
    protected fun enrol(user: User): String {
        val setUp =
            mvc
                .perform(json(post("/users/me/two-factor/setup"), """{"password":"Password123!"}""").with(signedIn(user, steppedUp = true)))
                .andReturn()
        val key = mapper.readTree(setUp.response.contentAsString).path("key").asString()
        mvc.perform(json(post("/users/me/two-factor/confirm"), """{"code":"${codeFor(key)}"}""").with(signedIn(user)))
        mvc.perform(post("/users/me/two-factor/saved").with(signedIn(user)))
        nextStep()
        return key
    }

    protected fun codeFor(key: String): String = Totp.code(Base32.decode(key), Totp.stepAt(clock.instant()))

    /** Moves the clock into the next time step, so a fresh code is not a replay of the last. */
    protected fun nextStep() = clock.advance(Duration.ofSeconds(30))

    /** The lock links the security notifications queued for [userId], oldest first. */
    protected fun lockLinks(userId: Long): List<String> =
        findJobsByType(EmailJobs.SecurityNotice.type)
            .map { mapper.readTree(it.payload) }
            .filter { it.path("audience").asString() != "ADMINISTRATOR" }
            .mapNotNull { payload -> payload.path("lockToken").takeIf { !it.isNull && !it.isMissingNode }?.asString() }
            .filter { recoveryTokens.findBySelector(it.substringBefore(".")).map { t -> t.user.id == userId }.orElse(false) }

    protected fun notices(audience: String? = null) =
        findJobsByType(EmailJobs.SecurityNotice.type)
            .map { mapper.readTree(it.payload) }
            .filter { audience == null || it.path("audience").asString() == audience }

    /** The raw link the recovery email of [purpose] carried to [userId], newest. */
    protected fun recoveryLink(
        userId: Long,
        purpose: TokenPurpose,
    ): String =
        findJobsByType(EmailJobs.Recovery.type)
            .map { mapper.readTree(it.payload) }
            .last { it.path("userId").asLong() == userId && it.path("tokenPurpose").asString() == purpose.name }
            .path("token")
            .asString()
}
