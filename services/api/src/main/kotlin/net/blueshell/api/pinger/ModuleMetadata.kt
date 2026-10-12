package net.blueshell.api.pinger

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * The SNTPings leaderboard, kept after the event: every contributor's final total and peak rate,
 * and the combined record. Nothing writes to it any more; the public page only reads it.
 */
@PackageInfo
@ApplicationModule(
    id = "pinger",
    allowedDependencies = [
        // Open kernel: members are shown by their Discord face or site username.
        "shared",
    ],
)
class ModuleMetadata
