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
@ApplicationModule(id = "email")
class ModuleMetadata
