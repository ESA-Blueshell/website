package net.blueshell.api.domain.committee

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Committees and who sits on them, as dated seat windows rather than a flat member list.
 *
 * Seat bookkeeping is this module's, not `user`'s: it listens for membership changes and
 * revokes seats itself, so a committee decides what its own membership means.
 */
@PackageInfo
@ApplicationModule(id = "committee")
class ModuleMetadata
