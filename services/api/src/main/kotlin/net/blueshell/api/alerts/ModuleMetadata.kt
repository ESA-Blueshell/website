package net.blueshell.api.alerts

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * What needs someone in Management, gathered from every module that owns a cause.
 *
 * A module raises alerts by implementing `AlertSource`, so this module reads no other module's
 * data; an alert clears itself once its source stops raising it. A person may hide an alert for
 * themself, which lasts until the alert clears.
 */
@PackageInfo
@ApplicationModule(
    id = "alerts",
    allowedDependencies = [
        // Open kernel: the page is @BoardOnly, and a source's audience is read off the reader's roles.
        "security",
        // Open kernel.
        "shared",
    ],
)
class ModuleMetadata
