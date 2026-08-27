package net.blueshell.api.platform.integration.sync

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Pushes an aggregate's current state to the external systems that mirror it and remembers the
 * resulting external id in `external_id_mapping`. Each destination implements the `SyncTarget`
 * port; the Google Calendar adapter is the sibling `calendar` package, folded in by the flattening.
 *
 * Fan-out runs off `@ApplicationModuleListener`s, so a failed push is retried from the event
 * publication registry instead of failing the transaction that caused it.
 */
@PackageInfo
@ApplicationModule(id = "sync")
class ModuleMetadata
