package net.blueshell.api.platform.integration.sync.application

import net.blueshell.api.shared.enums.TargetSystem

/**
 * Thrown when [ExternalIdMappingService.linkUser] is asked to assign an
 * external id that is already owned by a *different* local user.
 */
class ExternalIdConflictException(
    val existingUserId: Long,
    val system: TargetSystem,
    val externalId: String,
) : RuntimeException("External id '$externalId' on $system is already linked to user $existingUserId")
