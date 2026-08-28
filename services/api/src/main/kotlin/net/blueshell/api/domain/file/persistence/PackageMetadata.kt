package net.blueshell.api.domain.file.persistence

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * File aggregate. Published separately from `api` because an owning-side JPA reference may
 * cross a module boundary and has to compile — see API ADR-013. Only a module that names
 * `file :: entities` may reach it.
 */
@PackageInfo
@NamedInterface("entities")
class PackageMetadata
