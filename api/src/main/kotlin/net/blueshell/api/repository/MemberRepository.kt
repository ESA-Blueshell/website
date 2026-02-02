package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.Membership;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends BaseRepository<Membership> {
    boolean existsByUserId(Long userId);
}
