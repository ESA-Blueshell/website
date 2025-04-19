package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.Guest;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GuestRepository extends BaseRepository<Guest, Long> {

    Optional<Guest> findByAccessToken(String accessToken);
}
