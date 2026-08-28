package net.blueshell.api.platform.integration.email

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Everything the application sends: template rendering, the SMTP transport, an `Email` outbox row
 * per message carrying its delivery state, and bounce parsing off the IMAP mailbox.
 *
 * What an email says is built by the module that owns the subject and handed over as an
 * `EmailContent`, so this module renders and delivers without knowing what it is about.
 */
@PackageInfo
@ApplicationModule(
    id = "email",
    allowedDependencies = [
        // A send is recorded as a job execution through JobExecutionService.
        "jobs :: api",
        // DEBT. EmailManagementController reads JobExecution.status to decide
        // whether a retry is allowed. That decision belongs behind
        // jobs :: api rather than in a controller reading the row.
        "jobs :: entities",
        // Open kernel: EmailPermission extends the base evaluator.
        "security",
        // Open kernel.
        "shared",
    ],
)
class ModuleMetadata
