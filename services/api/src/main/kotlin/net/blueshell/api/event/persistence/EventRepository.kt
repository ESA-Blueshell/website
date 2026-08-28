package net.blueshell.api.event.persistence

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
    override fun findAll(spec: Specification<Event>, pageable: Pageable): Page<Event>

    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(pageable: Pageable): Page<Event>

    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(): MutableList<Event>

    @Query(value = "SELECT * FROM events WHERE id = :id", nativeQuery = true)
    fun findByIdIncludingDeleted(id: Long): Event?
}
