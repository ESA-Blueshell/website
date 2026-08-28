package net.blueshell.api.domain.user.application

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * `user`'s published surface: account, membership and profile lookups. Everything other
 * modules ask about a member goes through here.
 */
@PackageInfo
@NamedInterface("api")
class PackageMetadata
