package net.blueshell.api.common.event.job;

import net.blueshell.api.common.enums.ResetType;

public record RecoveryEmailEvent(Long userId, String token, ResetType resetType) {
}
