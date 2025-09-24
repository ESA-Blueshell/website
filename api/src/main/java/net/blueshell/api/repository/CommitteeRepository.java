package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.Committee;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommitteeRepository extends BaseRepository<Committee> {
    List<Committee> findALlByMembersUserIdEquals(Long userId);
}
