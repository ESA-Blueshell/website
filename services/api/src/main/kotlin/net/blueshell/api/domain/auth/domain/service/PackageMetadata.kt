package net.blueshell.api.domain.auth.domain.service

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * `auth`'s published surface: token generation and validation, which the security filter
 * chain calls on every request.
 */
@PackageInfo
@NamedInterface("api")
class PackageMetadata
