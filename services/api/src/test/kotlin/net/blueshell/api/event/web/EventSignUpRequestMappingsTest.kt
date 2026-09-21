package net.blueshell.api.event.web

import net.blueshell.api.survey.web.AnswerRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EventSignUpRequestMappingsTest {
    private fun request() =
        UpdateEventSignUpRequest(
            answers =
                mutableListOf(
                    AnswerRequest(questionId = 200L, textResponse = "Because", optionSelections = mutableListOf(true)),
                ),
            guest =
                CreateGuestRequest(
                    name = "Guest Gordon",
                    discord = "gordon#0001",
                    email = "gordon@example.com",
                    phoneNumber = "0611111111",
                ),
            userId = 9L,
            version = 4L,
        )

    @Test
    fun `a board edit carries the answers, the guest and the version it read`() {
        val data = request().asBoardData()

        assertThat(data.answers).singleElement().satisfies({
            assertThat(it.questionId).isEqualTo(200L)
            assertThat(it.textResponse).isEqualTo("Because")
        })
        assertThat(data.guest?.name).isEqualTo("Guest Gordon")
        assertThat(data.userId).isEqualTo(9L)
        assertThat(data.version).isEqualTo(4L)
    }

    @Test
    fun `the event id is a placeholder, because the sign-up names the event`() {
        assertThat(request().asBoardData().eventId).isZero()
    }

    @Test
    fun `a body carrying nothing maps to empty answers rather than null`() {
        val data = UpdateEventSignUpRequest().asBoardData()

        assertThat(data.answers).isEmpty()
        assertThat(data.guest).isNull()
        assertThat(data.userId).isNull()
    }

    @Test
    fun `a guest sent without a phone number maps to blank, which validation refuses`() {
        val data =
            UpdateEventSignUpRequest(
                guest = CreateGuestRequest(name = "Guest", discord = "g#0001", email = "g@example.com"),
            ).asBoardData()

        assertThat(data.guest?.phoneNumber).isEmpty()
    }
}
