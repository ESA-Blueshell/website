package net.blueshell.api.event.web

import net.blueshell.api.event.domain.EventSignUpData
import net.blueshell.api.event.domain.EventSignUpService
import net.blueshell.api.event.domain.EventSignUpUseCases
import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * The board-side endpoints translate a request into a command and a sign-up into a response.
 * Asserted without MockMvc so a dropped field fails here rather than only against a database.
 */
class EventSignUpControllerTest {
    private val service = mock<EventSignUpService>()
    private val useCases = mock<EventSignUpUseCases>()
    private val controller = EventSignUpController(service, useCases)

    // Stubbed rather than constructed: the audit fields Hibernate fills are lateinit, and a
    // response reads all of them.
    private fun guestSignUp(): EventSignUp {
        val stamp = Instant.parse("2026-02-20T12:34:00Z")
        val storedGuest =
            mockk<Guest> {
                every { id } returns 7L
                every { name } returns "Guest Gordon"
                every { discord } returns "gordon#0001"
                every { email } returns "gordon@example.com"
                every { phoneNumber } returns "0611111111"
                every { version } returns 1L
                every { createdAt } returns stamp
                every { updatedAt } returns stamp
            }
        return mockk {
            every { id } returns 44L
            every { eventId } returns 100L
            every { answers } returns emptySet()
            every { guest } returns storedGuest
            every { user } returns null
            every { version } returns 3L
            every { createdAt } returns stamp
            every { updatedAt } returns stamp
        }
    }

    @Test
    fun `the board edit names the sign-up and answers the sign-up it saved`() {
        val saved = guestSignUp()
        whenever(useCases.updateById(eq(44L), any())).thenReturn(saved)
        val request =
            UpdateEventSignUpRequest(
                guest =
                    CreateGuestRequest(
                        name = "Corrected Name",
                        discord = "gordon#0001",
                        email = "gordon@example.com",
                    ),
                version = 3L,
            )

        val response = controller.updateEventSignUpById(44L, request)

        val command = argumentCaptor<EventSignUpData>()
        verify(useCases).updateById(eq(44L), command.capture())
        assertThat(command.firstValue.guest?.name).isEqualTo("Corrected Name")
        assertThat(command.firstValue.version).isEqualTo(3L)
        assertThat(response.id).isEqualTo(44L)
        assertThat(response.kind).isEqualTo(EventSignUpKind.GUEST)
        assertThat(response.guest?.name).isEqualTo("Guest Gordon")
    }

    @Test
    fun `a removal passes the board's notify choice on`() {
        controller.deleteEventSignup(44L, guestAccessToken = null, notify = true)

        verify(useCases).delete(eventSignUpId = 44L, accessToken = null, notify = true)
    }

    @Test
    fun `a guest cancelling with their link asks for no email`() {
        controller.deleteEventSignup(44L, guestAccessToken = "TOKEN", notify = false)

        verify(useCases).delete(eventSignUpId = 44L, accessToken = "TOKEN", notify = false)
    }
}
