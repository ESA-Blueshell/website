package net.blueshell.api.factory.event.persistence

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.file.persistence.File
import net.blueshell.api.user.persistence.User
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class EventFactory(
    private val persistence: FactoryPersistenceSupport,
) {
    fun build(
        committee: Committee,
        approved: Boolean = true,
        membersOnly: Boolean = false,
        signUp: Boolean = true,
        title: String = "Event ${System.currentTimeMillis()}",
        signUpDeadline: Instant? = null,
        signUpLimit: Int? = null,
    ): Event =
        Event(
            committee = committee,
            title = title,
            description = "Event description",
            location = "Campus",
            startTime = Instant.now().plusSeconds(3600),
            endTime = Instant.now().plusSeconds(7200),
            approved = approved,
            membersOnly = membersOnly,
            signUp = signUp,
            signUpDeadline = signUpDeadline,
            signUpLimit = signUpLimit,
        )

    fun create(
        committee: Committee,
        approved: Boolean = true,
        membersOnly: Boolean = false,
        signUp: Boolean = true,
        title: String = "Event ${System.currentTimeMillis()}",
        signUpDeadline: Instant? = null,
        signUpLimit: Int? = null,
    ): Event = persistence.persist(build(committee, approved, membersOnly, signUp, title, signUpDeadline, signUpLimit))

    fun buildBanner(
        event: Event,
        file: File,
    ): EventBanner = EventBanner(event = event, file = file)

    fun createBanner(
        event: Event,
        file: File,
    ): EventBanner = persistence.persist(buildBanner(event, file))

    fun buildSignUp(
        event: Event,
        user: User? = null,
        guest: Guest? = null,
    ): EventSignUp =
        EventSignUp(
            event = event,
            userId = user?.id,
            guest = guest,
        )

    fun createSignUp(
        event: Event,
        user: User? = null,
        guest: Guest? = null,
    ): EventSignUp = persistence.persist(buildSignUp(event, user, guest))

    fun buildGuest(
        name: String = "Guest User",
        accessToken: String = "guest-token-${System.currentTimeMillis()}",
    ): Guest =
        Guest.withRawToken(
            name = name,
            discord = "guest#1234",
            email = "guest-${System.currentTimeMillis()}@example.com",
            phoneNumber = "+31612345678",
            accessToken = accessToken,
        )

    fun createGuest(
        name: String = "Guest User",
        accessToken: String = "guest-token-${System.currentTimeMillis()}",
    ): Guest = persistence.persist(buildGuest(name, accessToken))
}
