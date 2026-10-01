package net.blueshell.api.sync.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class LedgerAnnouncementsTest {
    @Test
    fun `says an event is announced once its events-info post is recorded`() {
        val ledger: PostLedger =
            mock {
                on { find(1L, DiscordArtefact.INFO_POST) } doReturn RecordedArtefact("m1", 0)
                on { find(2L, DiscordArtefact.INFO_POST) } doReturn null
            }

        assertThat(LedgerAnnouncements(ledger).announced(1L)).isTrue()
        assertThat(LedgerAnnouncements(ledger).announced(2L)).isFalse()
    }
}
