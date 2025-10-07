package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.File;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileRepository extends BaseRepository<File> {
    Optional<File> findByName(String name);
    Optional<File> findByEventBannerId(Long bannerId);
}
