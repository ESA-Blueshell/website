package net.blueshell.api.sponsor.domain

import net.blueshell.api.sponsor.persistence.Sponsor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class SponsorUseCasesTest {

    private val sponsorService = mock<SponsorService>()
    private val useCases = SponsorUseCases(sponsorService)

    private var sponsorIdSequence = 1L

    private fun sponsor(name: String, description: String): Sponsor =
        Sponsor(name = name, description = description).apply {
            setField(this, "id", sponsorIdSequence++)
            setField(this, "createdAt", Instant.parse("2024-01-01T00:00:00Z"))
            setField(this, "updatedAt", Instant.parse("2024-01-01T00:00:00Z"))
        }

    /** Audit fields are lateinit and id is framework-assigned, so tests seed them reflectively. */
    private fun setField(target: Any, name: String, value: Any?) {
        var current: Class<*>? = target::class.java
        while (current != null) {
            try {
                val field = current.getDeclaredField(name)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        error("No field '$name' on ${target::class.java.name}")
    }

    @Nested
    inner class Create {

        @Test
        fun `creates sponsor from the given fields`() {
            val captured = argumentCaptor<Sponsor>()
            whenever(sponsorService.create(captured.capture()))
                .thenReturn(sponsor("Sponsor A", "Description A"))

            val result = useCases.create(name = "Sponsor A", description = "Description A")

            assertThat(captured.firstValue.name).isEqualTo("Sponsor A")
            assertThat(captured.firstValue.description).isEqualTo("Description A")
            assertThat(result.name).isEqualTo("Sponsor A")
            assertThat(result.description).isEqualTo("Description A")
        }
    }

    @Nested
    inner class Update {

        @Test
        fun `updates sponsor fields and version`() {
            val existing = sponsor("Old", "Old Description").apply { version = 1L }
            whenever(sponsorService.findById(9L)).thenReturn(existing)
            whenever(sponsorService.update(existing)).thenReturn(existing)

            val result = useCases.update(
                id = 9L,
                name = "New",
                description = "New Description",
                version = 4L,
            )

            assertThat(result.name).isEqualTo("New")
            assertThat(result.description).isEqualTo("New Description")
            assertThat(existing.version).isEqualTo(4L)
        }
    }
}
