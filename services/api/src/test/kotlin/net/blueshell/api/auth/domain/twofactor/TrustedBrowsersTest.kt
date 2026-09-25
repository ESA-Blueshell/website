package net.blueshell.api.auth.domain.twofactor

import net.blueshell.api.auth.persistence.TrustedBrowser
import net.blueshell.api.auth.persistence.TrustedBrowserRepository
import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.security.Browser
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.util.Optional

class TrustedBrowsersTest {
    private val clock = SettableClock().apply { set(Instant.parse("2026-09-24T12:00:00Z")) }
    private val repository = mock<TrustedBrowserRepository>()
    private val users = mock<UserService>()
    private val trustedBrowsers = TrustedBrowsers(repository, users, clock)
    private val firefox = Browser("Firefox", "Linux")
    private val user =
        User(
            username = "alice",
            email = "a@example.com",
            password = "h",
            initials = "A",
            firstName = "A",
            lastName = "D",
        ).also { it.id = 7 }

    @BeforeEach
    fun setUp() {
        whenever(users.findById(7)).thenReturn(user)
        whenever(repository.save(any<TrustedBrowser>())).thenAnswer { (it.arguments[0] as TrustedBrowser).also { b -> b.id = 3 } }
    }

    private fun trusted(): Pair<TrustedBrowser, String> {
        val issued = trustedBrowsers.trust(7, firefox)
        val saved = argumentCaptor<TrustedBrowser>()
        verify(repository, atLeastOnce()).save(saved.capture())
        val browser = saved.lastValue
        whenever(repository.findBySelector(browser.selector)).thenReturn(Optional.of(browser))
        return browser to issued.cookieValue
    }

    @Test
    fun `trusting stores only a hash of the verifier, for thirty days`() {
        val (browser, cookie) = trusted()

        assertThat(cookie).startsWith("${browser.selector}.")
        assertThat(browser.verifierHash).hasSize(64).isNotEqualTo(cookie.substringAfter("."))
        assertThat(browser.expiresAt).isEqualTo(clock.instant().plus(TrustedBrowsers.LIFETIME))
        assertThat(browser.browserFamily to browser.browserPlatform).isEqualTo("Firefox" to "Linux")
    }

    @Test
    fun `a cookie is good once, in the same browser, for the same person, inside its thirty days`() {
        val (browser, cookie) = trusted()

        assertThat(trustedBrowsers.redeem(cookie, 8, firefox)).isNull()
        assertThat(trustedBrowsers.redeem(cookie, 7, Browser("Chrome", "Linux"))).isNull()
        val next = trustedBrowsers.redeem(cookie, 7, firefox)!!
        assertThat(next.ttl).isEqualTo(Duration.ofDays(30))
        assertThat(browser.lastUsedAt).isEqualTo(clock.instant())
        assertThat(trustedBrowsers.redeem(cookie, 7, firefox)).isNull()

        clock.advance(Duration.ofDays(30))
        assertThat(trustedBrowsers.redeem(next.cookieValue, 7, firefox)).isNull()
    }

    @Test
    fun `a cookie that is not one reads as nothing`() {
        assertThat(trustedBrowsers.redeem(null, 7, firefox)).isNull()
        assertThat(trustedBrowsers.redeem("no-dot", 7, firefox)).isNull()
        whenever(repository.findBySelector("sel")).thenReturn(Optional.empty())
        assertThat(trustedBrowsers.redeem("sel.ver", 7, firefox)).isNull()
    }

    @Test
    fun `the list holds live browsers only, and forgetting reaches only the person's own`() {
        val (browser, _) = trusted()
        val expired = TrustedBrowser(user, "old", "h", "Safari", "iOS", Instant.EPOCH, Instant.EPOCH).also { it.id = 4 }
        whenever(repository.findOf(7)).thenReturn(listOf(browser, expired))
        whenever(repository.findById(3)).thenReturn(Optional.of(browser))
        whenever(repository.findById(5)).thenReturn(Optional.empty())

        assertThat(trustedBrowsers.of(7)).containsExactly(browser)
        assertThat(trustedBrowsers.forget(8, 3)).isFalse()
        assertThat(trustedBrowsers.forget(7, 5)).isFalse()
        verify(repository, never()).purge(any())
        assertThat(trustedBrowsers.forget(7, 3)).isTrue()
        verify(repository).purge(3)

        trustedBrowsers.forgetAll(7)
        verify(repository).purgeOf(7)
    }
}
