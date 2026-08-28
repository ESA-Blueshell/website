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
@ApplicationModule(
    id = "esports",
    allowedDependencies = [
        // Open kernel: EsportsPermission extends the base evaluator.
        "security",
        // Open kernel.
        "shared",
        // Players are resolved through UserService and MemberProfileService.
        "user :: api",
        // DEBT. EsportsPageQueryService reads User rows to name a player. No
        // esports entity holds an FK into users — the roster stores the id. This
        // wants a player projection published through user :: api.
        "user :: entities",
    ],
)
class ModuleMetadata
