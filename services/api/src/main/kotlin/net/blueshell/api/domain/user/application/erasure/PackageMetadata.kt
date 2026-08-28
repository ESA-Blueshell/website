package net.blueshell.api.domain.user.application.erasure

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * Erasure and restore. Deletion anonymises the row and keeps a snapshot for the restore
 * window, so "is this account deleted" is a question only this service can answer.
 */
@PackageInfo
@NamedInterface("api")
class PackageMetadata
