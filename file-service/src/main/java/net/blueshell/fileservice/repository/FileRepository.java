package net.blueshell.fileservice.repository;

import net.blueshell.db.BaseRepository;
import net.blueshell.fileservice.model.File;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileRepository extends BaseRepository<File, Long> {
    Optional<File> findByName(String name);
}
