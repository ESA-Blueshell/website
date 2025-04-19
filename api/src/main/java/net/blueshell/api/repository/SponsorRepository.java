package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.File;
import net.blueshell.api.model.Sponsor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SponsorRepository extends BaseRepository<Sponsor, Long> {
    Optional<Sponsor> findByPicture(File picture);
}
