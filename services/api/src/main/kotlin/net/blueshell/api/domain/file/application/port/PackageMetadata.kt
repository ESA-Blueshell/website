package net.blueshell.api.domain.file.application.port

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * Lookups `file` declares and another module implements, so a banner resolves without
 * `file` depending on the module that owns the thing it is a banner for.
 */
@PackageInfo
@NamedInterface("api")
class PackageMetadata
