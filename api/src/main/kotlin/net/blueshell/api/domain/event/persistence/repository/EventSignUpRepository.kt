package net.blueshell.api.domain.event.persistence.repository

import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Suppress("FunctionName")
@Repository
interface EventSignUpRepository : BaseRepository<EventSignUp, Long> {
    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(spec: Specification<EventSignUp>?): MutableList<EventSignUp>

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(spec: Specification<EventSignUp>?, pageable: Pageable): Page<EventSignUp>

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    fun findByUser_IdAndEvent_Id(userId: Long, eventId: Long): Optional<EventSignUp>

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT es FROM EventSignUp es WHERE es.guest.accessToken = :accessToken")
    fun findByGuestAccessToken(@Param("accessToken") accessToken: String): MutableList<EventSignUp>

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    fun findByEvent_Id(eventId: Long): MutableList<EventSignUp>

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    fun findByGuestAccessTokenAndEvent_Id(accessToken: String, eventId: Long): Optional<EventSignUp>

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllByEventSignUpForm_Id(surveyId: Long): MutableSet<EventSignUp>
}
