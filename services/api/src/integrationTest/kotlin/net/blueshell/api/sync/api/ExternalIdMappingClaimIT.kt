package net.blueshell.api.sync.api

import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@SpringBootTest
class ExternalIdMappingClaimIT : UserTestSupport() {
    @Autowired
    private lateinit var mappings: ExternalIdMappingService

    @Test
    fun `lets one caller claim, keeps what it records, and frees the claim again`() {
        val soon = Instant.now().minusSeconds(60)

        assertThat(mappings.claim("EVENT", 9001, "DISCORD_EVENTS_INFO", soon)).isTrue()
        assertThat(mappings.claim("EVENT", 9001, "DISCORD_EVENTS_INFO", soon)).isFalse()

        mappings.record("EVENT", 9001, "DISCORD_EVENTS_INFO", "m1", 7)
        val kept = mappings.find("EVENT", 9001, "DISCORD_EVENTS_INFO")!!
        assertThat(kept.externalId to kept.syncedVersion).isEqualTo("m1" to 7L)
        assertThat(mappings.claim("EVENT", 9001, "DISCORD_EVENTS_INFO", Instant.now().plusSeconds(60))).isFalse()

        mappings.release("EVENT", 9001, "DISCORD_EVENTS_INFO")
        assertThat(mappings.find("EVENT", 9001, "DISCORD_EVENTS_INFO")).isNull()
        assertThat(mappings.claim("EVENT", 9001, "DISCORD_EVENTS_INFO", soon)).isTrue()
    }

    @Test
    fun `takes over a claim left empty past its time`() {
        assertThat(mappings.claim("EVENT", 9002, "DISCORD_EVENT", Instant.now().minusSeconds(60))).isTrue()

        assertThat(mappings.claim("EVENT", 9002, "DISCORD_EVENT", Instant.now().plusSeconds(60))).isTrue()
    }
}
