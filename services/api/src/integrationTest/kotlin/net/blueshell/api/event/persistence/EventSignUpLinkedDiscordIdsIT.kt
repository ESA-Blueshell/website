package net.blueshell.api.event.persistence

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
class EventSignUpLinkedDiscordIdsIT : UserTestSupport() {
    @Autowired
    private lateinit var eventSignUps: EventSignUpRepository

    private fun linked(discordId: String?): User =
        createUserWithRole(Role.MEMBER).let { user ->
            user.discordId = discordId
            userRepository.save(user)
        }

    private fun signUp(
        event: Event,
        user: User? = null,
        guest: Guest? = null,
    ): EventSignUp = persist(EventSignUp(event = event, userId = user?.id, guest = guest))

    @Test
    fun `lists the Discord IDs linked to an event's sign-ups, first sign-up first, and nobody else's`() {
        val start = Instant.now().plus(7, ChronoUnit.DAYS)
        val event =
            persist(
                Event(
                    committee = createCommitteeFixture(),
                    title = "LAN party",
                    location = "Campus",
                    startTime = start,
                    endTime = start.plus(3, ChronoUnit.HOURS),
                    approved = true,
                    membersOnly = false,
                    signUp = true,
                ),
            )
        val suffix = System.nanoTime().toString().takeLast(8)
        val later = linked("80351110224$suffix")
        val earlier = linked("80351110225$suffix")
        signUp(event, earlier)
        signUp(event, linked(null))
        signUp(event, linked(""))
        val guest = Guest.withRawToken(name = "Guest", discord = "guest", email = "guest@example.com", accessToken = "token-$suffix")
        signUp(event, guest = guest)
        signUp(event, later)
        val cancelled = signUp(event, linked("80351110226$suffix"))
        transactionTemplate.execute { eventSignUps.deleteById(cancelled.id!!) }

        assertThat(eventSignUps.findLinkedDiscordIds(event.id!!)).containsExactly(earlier.discordId, later.discordId)
    }
}
