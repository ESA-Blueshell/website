package net.blueshell.api.sponsor.web

import net.blueshell.api.sponsor.domain.SponsorUseCases
import net.blueshell.api.sponsor.persistence.Sponsor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class SponsorControllerReadTest {
    private val useCases = mock<SponsorUseCases>()
    private val controller = SponsorController(useCases)

    private val sponsor =
        Sponsor(name = "El Niño", description = "Digital development").also {
            it.id = 3
            it.createdAt = Instant.EPOCH
            it.updatedAt = Instant.EPOCH
        }

    @Test
    fun `reads every sponsor and one by id through the use cases`() {
        whenever(useCases.all()).thenReturn(listOf(sponsor))
        whenever(useCases.byId(3)).thenReturn(sponsor)

        assertThat(controller.findSponsors().map { it.name }).containsExactly("El Niño")
        assertThat(controller.findSponsorById(3).id).isEqualTo(3)
    }

    @Test
    fun `removes a sponsor through the use cases`() {
        controller.deleteSponsorById(3)

        verify(useCases).remove(3)
    }
}
