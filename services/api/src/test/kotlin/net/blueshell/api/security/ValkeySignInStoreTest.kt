package net.blueshell.api.security

import net.blueshell.api.testsupport.UnitValkey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ValkeySignInStoreTest {
    private val store = ValkeySignInStore(UnitValkey.template)
    private val now = Instant.parse("2026-09-24T12:00:00Z")
    private val later = Instant.now().plus(Duration.ofDays(1))

    private val signIn =
        SignIn(
            id = "s1",
            userId = 7,
            startedAt = now,
            lastSeenAt = now,
            browser = Browser("Firefox", "Linux"),
            securityStamp = 2,
            currentJti = "j1",
            currentIssuedAt = now,
            steppedUpAt = now,
            methods = setOf("pwd", "otp"),
        )

    @BeforeEach
    fun setUp() = UnitValkey.flush()

    @Test
    fun `a sign-in reads back as it was written, and is indexed under its person`() {
        store.save(signIn, later)

        assertThat(store.find("s1")).isEqualTo(signIn)
        assertThat(store.idsOf(7)).containsExactly("s1")
        assertThat(store.find("missing")).isNull()
    }

    @Test
    fun `saving again drops what the new copy no longer carries`() {
        store.save(signIn.copy(previousJti = "j0", previousRetiredAt = now), later)

        store.save(signIn.copy(steppedUpAt = null), later)

        val read = store.find("s1")!!
        assertThat(read.previousJti).isNull()
        assertThat(read.previousRetiredAt).isNull()
        assertThat(read.steppedUpAt).isNull()
    }

    @Test
    fun `a rotation replaces the token id only if it is still the one read`() {
        store.save(signIn, later)
        val at = now.plusSeconds(300)

        assertThat(store.rotate("s1", "stale", "j2", at, later)).isFalse()
        assertThat(store.rotate("s1", "j1", "j2", at, later)).isTrue()

        val read = store.find("s1")!!
        assertThat(read.currentJti).isEqualTo("j2")
        assertThat(read.previousJti).isEqualTo("j1")
        assertThat(read.previousRetiredAt).isNull()
        assertThat(read.lastSeenAt).isEqualTo(at)
        assertThat(store.rotate("s1", "j1", "j3", at, later)).isFalse()
    }

    @Test
    fun `the previous token id is retired once, and only for the token id that is current`() {
        store.save(signIn, later)
        val at = now.plusSeconds(300)
        store.rotate("s1", "j1", "j2", at, later)

        store.retirePrevious("s1", "j1", at.plusSeconds(1))
        assertThat(store.find("s1")!!.previousRetiredAt).isNull()

        store.retirePrevious("s1", "j2", at.plusSeconds(2))
        store.retirePrevious("s1", "j2", at.plusSeconds(3))
        assertThat(store.find("s1")!!.previousRetiredAt).isEqualTo(at.plusSeconds(2))

        store.rotate("s1", "j2", "j3", at.plusSeconds(300), later)
        assertThat(store.find("s1")!!.previousRetiredAt).isNull()
    }

    @Test
    fun `deleting takes the record and its index entry, and a stamp only moves forward`() {
        store.save(signIn, later)
        store.save(signIn.copy(id = "s2"), later)

        assertThat(store.delete("s1")).isTrue()
        assertThat(store.delete("s1")).isFalse()
        store.unindex(7, "s2")
        assertThat(store.delete("never-was")).isFalse()

        assertThat(store.find("s1")).isNull()
        assertThat(store.idsOf(7)).isEmpty()
        assertThat(store.securityStamp(7)).isZero()
        assertThat(store.bumpSecurityStamp(7)).isEqualTo(1)
        assertThat(store.bumpSecurityStamp(7)).isEqualTo(2)
        assertThat(store.securityStamp(7)).isEqualTo(2)
    }

    @Test
    fun `a record missing a field it needs reads as nothing`() {
        UnitValkey.template.opsForHash<String, String>().put("${ValkeySignInStore.KEY_PREFIX}broken", "userId", "7")

        assertThat(store.find("broken")).isNull()
    }
}
