package net.blueshell.api.sponsor.domain

import net.blueshell.api.sponsor.persistence.Sponsor
import net.blueshell.api.sponsor.persistence.SponsorRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

class SponsorServiceTest {
    @Test
    fun `reads a sponsor through its repository`() {
        val repository = mock<SponsorRepository>()
        val sponsor = Sponsor(name = "El Niño", description = "Digital development")
        whenever(repository.findById(7L)).thenReturn(Optional.of(sponsor))

        assertThat(SponsorService(repository, mock()).findById(7L)).isSameAs(sponsor)
    }
}
