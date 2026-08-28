package net.blueshell.api.platform.integration.sync.application

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * `sync`'s published surface: the fan-out services that drive each target system, and the
 * external-id mapping other modules resolve against.
 */
@PackageInfo
@NamedInterface("api")
class PackageMetadata
