package net.blueshell.api.platform.integration.sync.persistence

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * External-id mapping aggregate. Published separately from `api` because an owning-side
 * JPA reference may cross a module boundary and has to compile — see API ADR-013. Only a
 * module that names `sync :: entities` may reach it.
 */
@PackageInfo
@NamedInterface("entities")
class PackageMetadata
