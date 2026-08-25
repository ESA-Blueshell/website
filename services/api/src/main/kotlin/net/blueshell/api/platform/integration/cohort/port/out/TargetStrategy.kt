package net.blueshell.api.platform.integration.cohort.port.out

import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.shared.enums.TargetSystem

enum class TargetCapability { CATALOG, CREATE, READ_MEMBERS, WRITE_MEMBERS, DELETE, MOVE }

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

    /**
     * Every folder the system has, whether or not anything is filed in it.
     *
     * Read rather than inferred from the targets: a folder holding nothing is invisible to
     * the catalogue, and an empty folder is exactly where a target is most likely headed.
     */
    fun folders(): List<String> = emptyList()

    /**
     * File a target under another folder, and answer with where it ended up.
     *
     * Systems that cannot move one say so through [TargetCapability.MOVE] rather than by
     * failing when asked.
     */
    fun move(target: ExternalTarget, folder: String): ExternalTarget =
        throw UnsupportedOperationException("$system cannot move a target between folders")

    fun delete(target: ExternalTarget)
}
