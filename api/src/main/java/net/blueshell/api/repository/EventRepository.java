package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.File;
import net.blueshell.api.model.event.Event;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends BaseRepository<Event> {

    @NotNull
    @Query("SELECT e FROM Event e ORDER BY e.startTime DESC")
    @Override
    Page<Event> findAll(@NotNull Pageable pageable);

    @NotNull
    @Query("SELECT e FROM Event e ORDER BY e.startTime DESC")
    @Override
    List<Event> findAll();
}
