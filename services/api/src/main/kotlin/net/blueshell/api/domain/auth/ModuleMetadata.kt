package net.blueshell.api.domain.auth

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Getting in and getting an account: login, password recovery tokens, signup sessions and
 * member activation.
 *
 * Implements `user`'s `SignupCompletion` port — this module decides when a signup is complete,
 * `user` owns the account that results from it.
 */
@PackageInfo
@ApplicationModule(id = "auth")
class ModuleMetadata
