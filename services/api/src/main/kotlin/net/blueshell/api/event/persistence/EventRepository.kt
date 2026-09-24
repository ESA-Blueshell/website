package net.blueshell.api.event.persistence

import java.time.Instant

import org.springframework.data.repository.query.Param

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface EventRepository : BaseRepository<Event, Long> {
    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(
        spec: Specification<Event>,
        pageable: Pageable,
    ): Page<Event>

    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(pageable: Pageable): Page<Event>

    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(): MutableList<Event>

    @Query(value = "SELECT * FROM events WHERE id = :id", nativeQuery = true)
    fun findByIdIncludingDeleted(id: Long): Event?

    @Query("select e.id from Event e where e.approved = true and e.startTime <= :to and e.endTime >= :from")
    fun findApprovedIdsOverlapping(
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<Long>

    fun existsByTitle(title: String): Boolean

    @Query("select count(e) from Event e join e.gameCodes code where code = :code")
    fun countNamingGame(
        @Param("code") code: String,
    ): Long
}
