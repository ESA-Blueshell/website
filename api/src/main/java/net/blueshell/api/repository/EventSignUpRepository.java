package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventSignUpRepository extends BaseRepository<EventSignUp> {

    Optional<EventSignUp> findByUserIdAndEventId(Long userId, Long event);

    @Query("SELECT es FROM EventSignUp es WHERE es.guest.accessToken = :accessToken")
    Optional<EventSignUp> findByGuestAccessToken(@Param("accessToken") String accessToken);

    List<EventSignUp> findByUserId(Long user);

    List<EventSignUp> findByEventId(Long eventId);

    User user(User user);
}
