package net.blueshell.api.security

import net.blueshell.api.security.SignIns.Resolution
import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.testsupport.InMemorySignInStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant

class SignInsTest {
    private val clock = SettableClock().apply { set(Instant.parse("2026-09-24T12:00:00Z")) }
    private val store = InMemorySignInStore()
    private val published = mutableListOf<Any>()
    private val events = ApplicationEventPublisher { published += it }
    private val tokens =
        JwtTokenUtil("2goYh5PqH6dPkWWXLUJQ4QY6nD2YgR5qk9+6Yu8aITR7cfwxkuNolL9zkgf2qHFxifWdbxG+E+XqMIKkt3ibDw==", "api", "web", clock)
    private val signIns =
        SignIns(store, tokens, clock, events, Duration.ofDays(30), Duration.ofDays(14), Duration.ofMinutes(5), Duration.ofSeconds(60))
    private val firefox = Browser("Firefox", "Windows")

    private fun resolve(token: String, browser: Browser = firefox, mayRotate: Boolean = true) =
        signIns.resolve(token, browser, mayRotate)

    @Test
    fun `a fresh sign-in is honoured and not rotated`() {
        val issued = signIns.start(7, firefox)

        val resolution = resolve(issued.token)

        assertThat(resolution).isInstanceOf(Resolution.Honoured::class.java)
        assertThat((resolution as Resolution.Honoured).rotated).isNull()
        assertThat(issued.cookieTtl).isEqualTo(Duration.ofDays(30))
    }

    @Test
    fun `a cookie older than five minutes is rotated, and the old one is good for sixty seconds`() {
        val first = signIns.start(7, firefox)
        clock.advance(Duration.ofMinutes(5))

        val rotated = (resolve(first.token) as Resolution.Honoured).rotated!!

        clock.advance(Duration.ofSeconds(59))
        assertThat(resolve(first.token)).isInstanceOf(Resolution.Honoured::class.java)
        assertThat(resolve(rotated.token)).isInstanceOf(Resolution.Honoured::class.java)
    }

    @Test
    fun `an old cookie after its grace ends the sign-in for both holders`() {
        val first = signIns.start(7, firefox)
        clock.advance(Duration.ofMinutes(5))
        val rotated = (resolve(first.token) as Resolution.Honoured).rotated!!

        clock.advance(Duration.ofSeconds(61))

        assertThat(resolve(first.token)).isEqualTo(Resolution.Refused)
        assertThat(resolve(rotated.token)).isEqualTo(Resolution.Refused)
        assertThat(published.single()).isEqualTo(SignInEndedAsSuspicious(7, SignInEndReason.REUSED, firefox, clock.instant()))
    }

    @Test
    fun `a request from another browser family or system ends the sign-in`() {
        val issued = signIns.start(7, firefox)

        assertThat(resolve(issued.token, Browser("Chrome", "Windows"))).isEqualTo(Resolution.Refused)
        assertThat(resolve(issued.token)).isEqualTo(Resolution.Refused)
        assertThat((published.single() as SignInEndedAsSuspicious).reason).isEqualTo(SignInEndReason.BROWSER_CHANGED)
    }

    @Test
    fun `a sign-in used every day still ends thirty days after it began`() {
        var token = signIns.start(7, firefox).token
        repeat(29) {
            clock.advance(Duration.ofDays(1))
            token = (resolve(token) as Resolution.Honoured).rotated!!.token
        }

        clock.advance(Duration.ofDays(1).minusSeconds(1))
        assertThat(resolve(token)).isInstanceOf(Resolution.Honoured::class.java)
        clock.advance(Duration.ofSeconds(1))
        assertThat(resolve(token)).isEqualTo(Resolution.Refused)
    }

    @Test
    fun `a sign-in left alone ends after fourteen days`() {
        val issued = signIns.start(7, firefox)

        clock.advance(Duration.ofDays(14).minusSeconds(1))
        assertThat(signIns.isLive(issued.signIn.id)).isTrue()
        clock.advance(Duration.ofSeconds(1))
        assertThat(resolve(issued.token)).isEqualTo(Resolution.Refused)
    }

    @Test
    fun `a token that may not rotate is honoured as it is`() {
        val issued = signIns.start(7, firefox)
        clock.advance(Duration.ofMinutes(10))

        assertThat((resolve(issued.token, mayRotate = false) as Resolution.Honoured).rotated).isNull()
    }

    @Test
    fun `ending all sign-ins keeps the one the person asked from`() {
        val here = signIns.start(7, firefox)
        val elsewhere = signIns.start(7, Browser("Safari", "iOS"))
        val somebodyElse = signIns.start(8, firefox)

        signIns.endAll(7, keep = here.signIn.id)

        assertThat(resolve(here.token)).isInstanceOf(Resolution.Honoured::class.java)
        assertThat(resolve(elsewhere.token, Browser("Safari", "iOS"))).isEqualTo(Resolution.Refused)
        assertThat(resolve(somebodyElse.token)).isInstanceOf(Resolution.Honoured::class.java)
    }

    @Test
    fun `a bumped stamp ends a sign-in the store still holds`() {
        val issued = signIns.start(7, firefox)
        store.bumpSecurityStamp(7)

        assertThat(resolve(issued.token)).isEqualTo(Resolution.Refused)
    }

    @Test
    fun `the person's live sign-ins are listed newest first`() {
        val older = signIns.start(7, firefox)
        clock.advance(Duration.ofMinutes(1))
        val newer = signIns.start(7, Browser("Safari", "iOS"))
        signIns.start(8, firefox)

        assertThat(signIns.of(7).map { it.id }).containsExactly(newer.signIn.id, older.signIn.id)
    }

    @Test
    fun `a step-up is recorded on the sign-in and lasts its window`() {
        val issued = signIns.start(7, firefox)
        assertThat(signIns.steppedUpWithin(issued.signIn, Duration.ofMinutes(10))).isFalse()

        signIns.recordStepUp(issued.signIn.id, SignIn.METHOD_OTP)

        val signIn = signIns.find(issued.signIn.id)!!
        assertThat(signIn.steppedUpAt).isEqualTo(clock.instant())
        assertThat(signIn.methods).containsExactlyInAnyOrder("pwd", "otp")
        clock.advance(Duration.ofMinutes(10))
        assertThat(signIns.steppedUpWithin(signIn, Duration.ofMinutes(10))).isTrue()
        clock.advance(Duration.ofSeconds(1))
        assertThat(signIns.steppedUpWithin(signIn, Duration.ofMinutes(10))).isFalse()
    }

    @Test
    fun `a token naming a sign-in that is gone is refused`() {
        val issued = signIns.start(7, firefox)
        signIns.end(issued.signIn.id)

        assertThat(resolve(issued.token)).isEqualTo(Resolution.Refused)
        assertThat(resolve("garbage")).isEqualTo(Resolution.Refused)
    }
}
