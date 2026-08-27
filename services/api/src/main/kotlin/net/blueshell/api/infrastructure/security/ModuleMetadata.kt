package net.blueshell.api.infrastructure.security

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Authentication plumbing every request passes through: JWT generation and revocation, the auth
 * filter and entry point, the rate limiter in front of the public auth endpoints, and the
 * `CurrentUserProvider` that reads the security context.
 *
 * Open, because every module's own `Permission` extends the base evaluator held here — a closed
 * module would sit in a cycle with all twenty of them.
 */
@PackageInfo
@ApplicationModule(id = "security", type = ApplicationModule.Type.OPEN)
class ModuleMetadata
