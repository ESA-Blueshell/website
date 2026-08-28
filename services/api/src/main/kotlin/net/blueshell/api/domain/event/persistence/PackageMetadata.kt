package net.blueshell.api.domain.event.persistence

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * Event aggregate. Published separately from `api` because an owning-side JPA reference
 * may cross a module boundary and has to compile — see API ADR-013. Only a module that
 * names `event :: entities` may reach it.
 */
@PackageInfo
@NamedInterface("entities")
class PackageMetadata
