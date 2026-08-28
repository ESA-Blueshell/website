package net.blueshell.api.jobs.web

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * Debt: reached from outside the module and pinned by the cross-module web ratchet.
 */
@PackageInfo
@NamedInterface("legacy-web")
class PackageMetadata
