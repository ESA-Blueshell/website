package net.blueshell.api.exceptions

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * The api's own faults: every exception a request or a job did not handle, grouped by its type
 * and where it was thrown, so one fault is one `RecordedException` however often it fires.
 *
 * Admins list, open and resolve them; a resolved fault reopens when it fires again.
 */
@PackageInfo
@ApplicationModule(
    id = "exceptions",
    allowedDependencies = [
        // Open faults raise an alert.
        "alerts :: api",
        // Open kernel: the pages are @AdminOnly.
        "security",
        // Open kernel.
        "shared",
    ],
)
class ModuleMetadata
