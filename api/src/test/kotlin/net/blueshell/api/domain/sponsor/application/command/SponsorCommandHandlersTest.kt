package net.blueshell.api.domain.sponsor.application.command

import net.blueshell.api.domain.sponsor.application.SponsorService
import net.blueshell.api.domain.sponsor.command.CreateSponsorCommand
import net.blueshell.api.domain.sponsor.command.DeleteSponsorByIdCommand
import net.blueshell.api.domain.sponsor.command.FindSponsorByIdCommand
import net.blueshell.api.domain.sponsor.command.FindSponsorsCommand
import net.blueshell.api.domain.sponsor.command.UpdateSponsorCommand
import net.blueshell.api.domain.sponsor.persistence.Sponsor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class SponsorCommandHandlersTest {

    private val sponsorService = mock<SponsorService>()

    @Nested
    inner class FindSponsors {

        private val handler = FindSponsorsHandler(sponsorService)

        @Test
        fun `returns mapped sponsor results`() {
            val first = sponsor("First", "Desc 1")
            val second = sponsor("Second", "Desc 2")
            whenever(sponsorService.findAll()).thenReturn(mutableListOf(first, second))

            val result = handler.handle(FindSponsorsCommand())

            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactly("First", "Second")
        }
    }

    @Nested
    inner class CreateSponsor {

        private val handler = CreateSponsorHandler(sponsorService)

        @Test
        fun `creates sponsor from command fields`() {
            val captured = argumentCaptor<Sponsor>()
            whenever(sponsorService.create(captured.capture())).thenReturn(sponsor("Sponsor A", "Description A"))

            val result = handler.handle(CreateSponsorCommand(name = "Sponsor A", description = "Description A"))

            assertThat(captured.firstValue.name).isEqualTo("Sponsor A")
            assertThat(captured.firstValue.description).isEqualTo("Description A")
            assertThat(result.name).isEqualTo("Sponsor A")
            assertThat(result.description).isEqualTo("Description A")
        }
    }

    @Nested
    inner class UpdateSponsor {

        private val handler = UpdateSponsorHandler(sponsorService)

        @Test
        fun `updates sponsor fields and version`() {
            val existing = sponsor("Old", "Old Description").apply { version = 1L }
            whenever(sponsorService.findById(9L)).thenReturn(existing)
            whenever(sponsorService.update(existing)).thenReturn(existing)

            val result = handler.handle(
                UpdateSponsorCommand(
                    id = 9L,
                    name = "New",
                    description = "New Description",
                    version = 4L
                )
            )

            assertThat(result.name).isEqualTo("New")
            assertThat(result.description).isEqualTo("New Description")
            assertThat(existing.version).isEqualTo(4L)
        }
    }

    @Nested
    inner class FindSponsorById {

        private val handler = FindSponsorByIdHandler(sponsorService)

        @Test
        fun `returns mapped sponsor result for id`() {
            whenever(sponsorService.findById(3L)).thenReturn(sponsor("Sponsor 3", "Desc"))

            val result = handler.handle(FindSponsorByIdCommand(3L))

            assertThat(result.name).isEqualTo("Sponsor 3")
        }
    }

    @Nested
    inner class DeleteSponsorById {

        private val handler = DeleteSponsorByIdHandler(sponsorService)

        @Test
        fun `deletes sponsor by id`() {
            handler.handle(DeleteSponsorByIdCommand(12L))

            verify(sponsorService).deleteById(eq(12L))
        }
    }

    private fun sponsor(name: String, description: String): Sponsor = Sponsor(
        name = name,
        description = description
    ).apply {
        setField(this, "createdAt", Instant.parse("2024-01-01T00:00:00Z"))
        setField(this, "updatedAt", Instant.parse("2024-01-01T00:00:00Z"))
    }

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
        error("Field $name not found on ${target::class.java.name}")
    }
}
