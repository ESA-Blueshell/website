package net.blueshell.api.sponsor.domain

import net.blueshell.api.sponsor.persistence.Sponsor
import net.blueshell.api.sponsor.persistence.SponsorRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.OptimisticLockingFailureException
import java.time.Instant
import java.util.Optional

class SponsorUseCasesTest {
    private val sponsors = mock<SponsorRepository>()
    private val useCases = SponsorUseCases(sponsors)

    private var sponsorIdSequence = 1L

    private fun sponsor(
        name: String,
        description: String,
    ): Sponsor =
        Sponsor(name = name, description = description).apply {
            setField(this, "id", sponsorIdSequence++)
            setField(this, "createdAt", Instant.parse("2024-01-01T00:00:00Z"))
            setField(this, "updatedAt", Instant.parse("2024-01-01T00:00:00Z"))
        }

    /** Audit fields are lateinit and id is framework-assigned, so tests seed them reflectively. */
    private fun setField(
        target: Any,
        name: String,
        value: Any?,
    ) {
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
            whenever(sponsors.saveAndFlush(captured.capture()))
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
        fun `updates sponsor fields, keeping the version it was read at`() {
            val existing = sponsor("Old", "Old Description").apply { version = 1L }
            whenever(sponsors.findById(9L)).thenReturn(Optional.of(existing))
            whenever(sponsors.saveAndFlush(existing)).thenReturn(existing)

            val result =
                useCases.update(
                    id = 9L,
                    name = "New",
                    description = "New Description",
                    version = 1L,
                )

            assertThat(result.name).isEqualTo("New")
            assertThat(result.description).isEqualTo("New Description")
            assertThat(existing.version).isEqualTo(1L)
        }

        @Test
        fun `refuses an edit made against an older version, before touching a field`() {
            val existing = sponsor("Old", "Old Description").apply { version = 2L }
            whenever(sponsors.findById(9L)).thenReturn(Optional.of(existing))

            assertThatThrownBy { useCases.update(id = 9L, name = "New", description = "New", version = 1L) }
                .isInstanceOf(OptimisticLockingFailureException::class.java)
            assertThat(existing.name).isEqualTo("Old")
            verify(sponsors, never()).saveAndFlush(any())
        }
    }

    @Nested
    inner class ReadAndRemove {
        @Test
        fun `reads every sponsor, and one by id`() {
            val existing = sponsor("Sponsor A", "Description A")
            whenever(sponsors.findAll()).thenReturn(mutableListOf(existing))
            whenever(sponsors.findById(existing.id!!)).thenReturn(Optional.of(existing))

            assertThat(useCases.all()).containsExactly(existing)
            assertThat(useCases.byId(existing.id!!)).isSameAs(existing)
        }

        @Test
        fun `refuses a sponsor that does not exist`() {
            whenever(sponsors.findById(404L)).thenReturn(Optional.empty())

            assertThatThrownBy { useCases.byId(404L) }.isInstanceOf(SponsorNotFound::class.java)
        }

        @Test
        fun `removes the sponsor it read`() {
            val existing = sponsor("Sponsor A", "Description A")
            whenever(sponsors.findById(existing.id!!)).thenReturn(Optional.of(existing))

            useCases.remove(existing.id!!)

            verify(sponsors).delete(existing)
        }
    }
}
