package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.BrevoChoice
import net.blueshell.api.cohort.domain.BrevoPlace
import net.blueshell.api.cohort.domain.CohortBrevo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BrevoPlaceControllerTest {
    private val brevo: CohortBrevo = mock()
    private val controller = BrevoPlaceController(brevo, "Committees")
    private val place = BrevoPlace(true, "7", "Sitecie", "Committees")

    @Test
    fun `reads and sets a committee's list, new lists going in Committees`() {
        whenever(brevo.read("COMMITTEE_MEMBERS:7")).thenReturn(place)
        whenever(brevo.apply("COMMITTEE_MEMBERS:7", BrevoChoice(listId = "9"), "Committees")).thenReturn(place)

        assertThat(controller.findCommitteeBrevo(7)).isSameAs(place)
        assertThat(controller.setCommitteeBrevo(7, BrevoPlaceRequest(listId = "9"))).isSameAs(place)
        assertThat(BrevoPlaceRequest().createList).isFalse()
    }
}
