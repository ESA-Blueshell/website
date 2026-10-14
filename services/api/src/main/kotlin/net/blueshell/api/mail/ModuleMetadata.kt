package net.blueshell.api.mail

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Mail the board writes on the site: an email addressed to cohorts, roles and people, sent in the
 * association's template as one email per person. What it sends goes through the email module and
 * shows in Sent like any other email.
 */
@PackageInfo
@ApplicationModule(
    id = "mail",
    allowedDependencies = [
        // Who is in a cohort such as Active members 2026-2027.
        "cohort :: api",
        // Each email goes out as an EmailJob, rendered from the site's markdown.
        "email :: api",
        // EmailJob extends the jobs module's handler.
        "jobs :: api",
        // Open kernel: the writing routes are @BoardOnly.
        "security",
        // Open kernel.
        "shared",
        // Who holds a role, and the person each email goes to.
        "user :: api",
        // The User the job reads a name and address from.
        "user :: entities",
    ],
)
class ModuleMetadata
