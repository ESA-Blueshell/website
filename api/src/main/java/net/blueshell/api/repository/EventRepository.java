package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.File;
import net.blueshell.api.model.User;
import net.blueshell.api.model.event.Event;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    Event findByBanner(File banner);

    @Query("SELECT e FROM Event e WHERE e.startTime >= CURRENT_DATE ORDER BY e.startTime DESC")
    List<Event> findUpcoming();

    @Query("""
            SELECT  e
            FROM    Event e
            LEFT JOIN CommitteeMember cm
                   ON cm.committee = e.committee
                  AND cm.user = :user
            WHERE   (:from IS NULL OR e.startTime >= :from)
              AND   (:to   IS NULL OR e.startTime <= :to)
              AND   e.visible = TRUE
              AND   (e.membersOnly = FALSE
                     OR (
                         cm IS NOT NULL
                         AND EXISTS (
                                SELECT 1
                                FROM User u
                                JOIN u.roles r
                                WHERE u = :user
                                  AND r in ('MEMBER', 'COMMITTEE', 'BOARD', 'TREASURER', 'ADMIN', 'SYSTEM')
                             )
                        )
                    )
            ORDER BY e.startTime DESC
            """)
    List<Event> findStartTimeBetweenAndVisibleToUser(@Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to,
                                                     @Param("user") User user);
}
