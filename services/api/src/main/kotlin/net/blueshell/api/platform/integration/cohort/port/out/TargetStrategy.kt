package net.blueshell.api.platform.integration.cohort.port.out

import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.shared.enums.TargetSystem

enum class TargetCapability { CATALOG, CREATE, READ_MEMBERS, WRITE_MEMBERS, DELETE }

data class TargetDescriptor(
    val system: TargetSystem,
    val kind: CohortKind,
    val systemLabel: String,
    val targetLabel: String,
    val idLabel: String,
    val folderLabel: String? = null,
    val capabilities: Set<TargetCapability>,
) {
    fun supports(capability: TargetCapability): Boolean = capability in capabilities
}

data class ExternalTarget(
    val system: TargetSystem,
    val externalId: String,
    val kind: CohortKind,
    val label: String,
    val folderLabel: String? = null,
    val memberCount: Long? = null,
    val linkedCohortId: Long? = null,
)

data class ExternalMember(val externalUserId: String, val label: String?)

interface TargetStrategy {
    val descriptor: TargetDescriptor
    val system: TargetSystem get() = descriptor.system

    fun catalog(query: String?): List<ExternalTarget> = emptyList()

    fun resolve(externalId: String): ExternalTarget? =
        catalog(externalId).firstOrNull { it.externalId == externalId }

    fun members(target: ExternalTarget): List<ExternalMember>

    fun add(target: ExternalTarget, externalUserId: String)

    fun remove(target: ExternalTarget, externalUserId: String)

    fun create(label: String, folder: String?): ExternalTarget

    fun delete(target: ExternalTarget)
}
