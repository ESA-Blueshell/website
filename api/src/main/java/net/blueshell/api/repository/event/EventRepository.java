package net.blueshell.api.repository.event;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends BaseRepository<Event> {

    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    @Override
    @NotNull
    Page<Event> findAll(@Nullable Specification<Event> spec, @NotNull Pageable pageable);

    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    @NotNull
    @Override
    Page<Event> findAll(@NotNull Pageable pageable);

    @EntityGraph(value = "Event.withBannerFileAndFormQuestions", type = EntityGraph.EntityGraphType.LOAD)
    @NotNull
    @Override
    List<Event> findAll();
}
