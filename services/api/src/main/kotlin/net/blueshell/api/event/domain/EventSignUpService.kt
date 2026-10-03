package net.blueshell.api.event.domain

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.event.api.EventSignUpsChanged
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.EventSignUpRepository
import net.blueshell.api.event.persistence.EventSignUpSpecifications
import net.blueshell.api.event.persistence.GuestAccessTokenCodec
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.function.Supplier

@Service
class EventSignUpService
    @Autowired
    constructor(
        private val repository: EventSignUpRepository,
        private val trackedEvents: TrackedEventPublisher,
        private val currentUserProvider: CurrentUserProvider,
    ) {
        // Read back after each write, so the columns the database fills are on the answer.
        @PersistenceContext
        private lateinit var em: EntityManager

        private fun written(signUp: EventSignUp): EventSignUp = repository.saveAndFlush(signUp).also(em::refresh)

        @Transactional(readOnly = true)
        fun findById(id: Long): EventSignUp =
            repository.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "EventSignUp not found with id: $id") }

        /** [confirmToGuest] is off for a sign-up the board adds, which mails only where the board asks. */
        @Transactional
        fun create(
            entity: EventSignUp,
            confirmToGuest: Boolean = true,
        ): EventSignUp {
            val saved = written(entity)
            trackedEvents.publish { actor ->
                EventSignUpCreated(
                    saved.id!!,
                    guestAccessToken = saved.guest?.accessTokenRaw.takeIf { confirmToGuest },
                    actor = actor,
                )
            }
            countMoved(saved.eventId)
            return saved
        }

        @Transactional
        fun deleteById(id: Long) = delete(findById(id))

        @Transactional
        fun delete(entity: EventSignUp) {
            repository.delete(entity)
            countMoved(entity.eventId)
        }

        /** Takes sign-ups away with no count published, for an event whose form is being replaced. */
        @Transactional
        fun deleteAll(signUps: Set<EventSignUp>) = repository.deleteAll(signUps)

        // Within the change's transaction, like EventChanged: the jobs queued for it commit with it.
        private fun countMoved(eventId: Long) = trackedEvents.publishWithin { actor -> EventSignUpsChanged(eventId, actor) }

        @Transactional
        fun update(entity: EventSignUp): EventSignUp = written(entity)

        @Transactional(readOnly = true)
        fun existsByUserIdAndEventId(
            userId: Long,
            eventId: Long,
        ): Boolean = repository.existsByUser_IdAndEvent_Id(userId, eventId)

        @Transactional(readOnly = true)
        fun findByUserIdAndEventId(
            userId: Long,
            eventId: Long,
        ): EventSignUp =
            repository
                .findByUser_IdAndEvent_Id(userId, eventId)
                .orElseThrow(
                    Supplier {
                        ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "EventSignUp not found for user: $userId and event: $eventId",
                        )
                    },
                )

        @Transactional(readOnly = true)
        fun findByGuestAccessToken(accessToken: String): MutableList<EventSignUp> =
            repository.findByGuestAccessTokenHash(GuestAccessTokenCodec.hash(accessToken))

        fun findByEventId(eventId: Long): MutableList<EventSignUp> = repository.findByEvent_Id(eventId)

        fun findByFilter(filter: EventSignUpQuery): MutableList<EventSignUp> {
            val spec = EventSignUpSpecifications.fromFilter(filter, currentUserProvider.currentUser())
            return repository.findAll(spec)
        }

        fun findBySurveyId(surveyId: Long): MutableSet<EventSignUp> = repository.findAllByEventSignUpForm_Id(surveyId)

        fun findByGuestAccessTokenAndEventId(
            accessToken: String,
            eventId: Long,
        ): EventSignUp =
            repository
                .findByGuestAccessTokenHashAndEvent_Id(GuestAccessTokenCodec.hash(accessToken), eventId)
                .orElseThrow(
                    Supplier {
                        ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "EventSignUp not found for provided guest token and event: $eventId",
                        )
                    },
                )
    }
