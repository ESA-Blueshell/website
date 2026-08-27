package net.blueshell.api.domain.esports

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Teams, the seasons they play and the roster entries that place a member in one, plus the
 * per-game accounts members link to themselves.
 *
 * The public esports page is assembled here as a single read model rather than joined together
 * by the frontend from several endpoints.
 */
@PackageInfo
@ApplicationModule(id = "esports")
class ModuleMetadata
