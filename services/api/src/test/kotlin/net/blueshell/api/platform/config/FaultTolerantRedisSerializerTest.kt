package net.blueshell.api.platform.config

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FaultTolerantRedisSerializerTest {

    private val serializer = FaultTolerantRedisSerializer()

    @Test
    fun `round-trips a serializable value`() {
        val bytes = serializer.serialize("hello")
        assertThat(serializer.deserialize(bytes)).isEqualTo("hello")
    }

    @Test
    fun `round-trips a UserPrincipal (the security-context payload)`() {
        val principal = UserPrincipal(
            id = 1L,
            usernameValue = "alice",
            passwordValue = "secret",
            enabledValue = true,
            roles = setOf(Role.MEMBER),
            addressId = null,
            personDetailsId = null,
        )
        assertThat(serializer.deserialize(serializer.serialize(principal))).isEqualTo(principal)
    }

    @Test
    fun `returns null instead of throwing for bytes it cannot deserialize`() {
        // A value left by a previous deploy that no longer deserializes — modelled
        // here by bytes that are not a valid JDK object stream at all.
        val unreadable = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        assertThat(serializer.deserialize(unreadable)).isNull()
    }

    @Test
    fun `deserializes null and empty input to null`() {
        assertThat(serializer.deserialize(null)).isNull()
        assertThat(serializer.deserialize(ByteArray(0))).isNull()
    }
}
