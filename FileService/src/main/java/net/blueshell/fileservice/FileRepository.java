package net.blueshell.fileservice;

import net.blueshell.db.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileRepository extends BaseRepository<File, Long> {
    Optional<File> findByName(String name);
}
