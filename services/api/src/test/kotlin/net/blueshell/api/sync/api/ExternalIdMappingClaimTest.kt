package net.blueshell.api.sync.api

import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.persistence.ExternalIdMappingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class ExternalIdMappingClaimTest {
    private val repository: ExternalIdMappingRepository = mock()
    private val service = ExternalIdMappingService(repository)
    private val stale = Instant.parse("2026-09-26T05:45:00Z")

    @Test
    fun `lets exactly one caller claim, or take over a claim left empty too long`() {
        whenever(repository.insertClaim("EVENT", 42, "S")).thenReturn(1, 0, 0)
        whenever(repository.takeOverStaleClaim("EVENT", 42, "S", stale)).thenReturn(0, 1)

        assertThat(service.claim("EVENT", 42, "S", stale)).isTrue()
        assertThat(service.claim("EVENT", 42, "S", stale)).isFalse()
        assertThat(service.claim("EVENT", 42, "S", stale)).isTrue()
    }

    @Test
    fun `records what a claim created, on the claim's row or a new one, and releases it`() {
        val claimed = ExternalIdMapping("EVENT", 42, "S")
        whenever(repository.findByAggregateTypeAndAggregateIdAndSystem("EVENT", 42, "S")).thenReturn(claimed, null)

        service.record("EVENT", 42, "S", "m1", 7)
        service.record("EVENT", 42, "S", "m2", 8)
        service.release("EVENT", 42, "S")

        val saved = argumentCaptor<ExternalIdMapping>()
        verify(repository, times(2)).save(saved.capture())
        assertThat(saved.firstValue).isSameAs(claimed)
        assertThat(claimed.externalId to claimed.syncedVersion).isEqualTo("m1" to 7L)
        assertThat(saved.secondValue.externalId to saved.secondValue.syncedVersion).isEqualTo("m2" to 8L)
        verify(repository).deleteMapping("EVENT", 42, "S")
    }
}
