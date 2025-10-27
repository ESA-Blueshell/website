package net.blueshell.api.repository.event;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.event.EventSignUp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface EventSignUpRepository extends BaseRepository<EventSignUp> {

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    @Override
    @NotNull
    List<EventSignUp> findAll(@Nullable Specification<EventSignUp> spec);

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    @Override
    @NotNull
    Page<EventSignUp> findAll(@Nullable Specification<EventSignUp> spec, @NotNull Pageable pageable);

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    Optional<EventSignUp> findByUserIdAndEventId(Long userId, Long eventId);

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT es FROM EventSignUp es WHERE es.guest.accessToken = :accessToken")
    List<EventSignUp> findByGuestAccessToken(@Param("accessToken") String accessToken);

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    List<EventSignUp> findByEventId(Long eventId);

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    Optional<EventSignUp> findByGuestAccessTokenAndEventId(String accessToken, Long eventId);

    @EntityGraph(value = "EventSignUp.withGuestAndAnswers", type = EntityGraph.EntityGraphType.LOAD)
    Set<EventSignUp> findAllByEventSignUpFormId(Long surveyId);
}
