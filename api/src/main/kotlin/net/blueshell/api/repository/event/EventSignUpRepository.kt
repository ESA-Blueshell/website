package net.blueshell.api.repository.event

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.event.EventSignUp
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EventSignUpRepository : BaseRepository<EventSignUp, Long> {
    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(spec: Specification<EventSignUp>): MutableList<EventSignUp>

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(spec: Specification<EventSignUp>, pageable: Pageable): Page<EventSignUp>

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    fun findByUserIdAndEventId(userId: Long, eventId: Long): Optional<EventSignUp>

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT es FROM EventSignUp es WHERE es.guest.accessToken = :accessToken")
    fun findByGuestAccessToken(@Param("accessToken") accessToken: String): MutableList<EventSignUp>

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    fun findByEventId(eventId: Long): MutableList<EventSignUp>

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    fun findByGuestAccessTokenAndEventId(accessToken: String, eventId: Long): Optional<EventSignUp>

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllByEventSignUpFormId(surveyId: Long): MutableSet<EventSignUp>
}
