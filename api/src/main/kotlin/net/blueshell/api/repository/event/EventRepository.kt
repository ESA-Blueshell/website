package net.blueshell.api.repository.event

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.event.Event
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.stereotype.Repository

@Repository
interface EventRepository : BaseRepository<Event?> {
    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(spec: Specification<Event?>?, pageable: Pageable): Page<Event?>

    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(pageable: Pageable): Page<Event?>

    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    override fun findAll(): MutableList<Event?>
}
