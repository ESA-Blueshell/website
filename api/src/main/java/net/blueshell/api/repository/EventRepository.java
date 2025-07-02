package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.Event;
import net.blueshell.api.model.EventPicture;
import net.blueshell.api.model.File;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface EventRepository extends BaseRepository<Event, Long> {

    @NotNull
    @Query("SELECT e FROM Event e ORDER BY e.startTime DESC")
    @Override
    Page<Event> findAll(@NotNull Pageable pageable);

    @NotNull
    @Query("SELECT e FROM Event e ORDER BY e.startTime DESC")
    @Override
    List<Event> findAll();

    Event findByBanner(File banner);

    @Query("SELECT e FROM Event e WHERE e.startTime >= CURRENT_DATE ORDER BY e.startTime DESC")
    List<Event> findUpcoming();

    @Query("""
       SELECT  e
       FROM    Event e
       WHERE   (:from IS NULL OR e.startTime >= :from)
         AND   (:to   IS NULL OR e.startTime <= :to)
       ORDER BY e.startTime DESC
       """)
    List<Event> findStartTimeBetween(@Param("from") LocalDateTime from,
                                     @Param("to")   LocalDateTime to);

    Optional<Event> findByEventPictures(Set<EventPicture> eventPicture);
}
