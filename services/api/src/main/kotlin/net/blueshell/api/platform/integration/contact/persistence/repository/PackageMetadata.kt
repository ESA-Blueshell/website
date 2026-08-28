package net.blueshell.api.platform.integration.contact.persistence.repository

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * Not a surface. A repository is persistence, and a module reaching another module's repository
 * is the reaching-into-persistence this ADR set exists to stop. Named here only so the one
 * whitelist entry that carries it says debt rather than `entities`.
 *
 * TODO: `sync` writes `Contact` rows through `ContactRepository` from `ContactSyncService`.
 * Routing that through a `contact` service is design work, not a rename — delete this
 * declaration once `contact` owns the write.
 */
@PackageInfo
@NamedInterface("legacy-repository")
class PackageMetadata
