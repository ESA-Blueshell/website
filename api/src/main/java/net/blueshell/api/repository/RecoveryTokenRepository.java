package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.model.RecoveryToken;

import java.util.List;
import java.util.Optional;

public interface RecoveryTokenRepository extends BaseRepository<RecoveryToken> {

    Optional<RecoveryToken> findBySelector(String selector);

    List<RecoveryToken> findAllByUser_IdAndTypeAndConsumedAtIsNull(Long userId, ResetType type);
}