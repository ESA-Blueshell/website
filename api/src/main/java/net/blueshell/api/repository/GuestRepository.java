package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.event.Guest;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GuestRepository extends BaseRepository<Guest> {

    Optional<Guest> findByAccessToken(String accessToken);
}
