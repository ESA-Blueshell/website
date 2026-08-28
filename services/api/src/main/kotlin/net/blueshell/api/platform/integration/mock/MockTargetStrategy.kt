package net.blueshell.api.platform.integration.mock

import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.domain.ExternalMember
import net.blueshell.api.cohort.domain.ExternalTarget
import net.blueshell.api.cohort.domain.TargetCapability
import net.blueshell.api.cohort.domain.TargetDescriptor
import net.blueshell.api.cohort.domain.TargetStrategy
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
@Primary
@Profile("test | dev")
class MockTargetStrategy : TargetStrategy {
    override val descriptor = TargetDescriptor(
        system = TargetSystem.BREVO,
        kind = CohortKind.LIST,
        systemLabel = "Brevo",
        targetLabel = "Brevo list",
        idLabel = "List id",
        folderLabel = "Folder",
        capabilities = setOf(
            TargetCapability.CATALOG,
            TargetCapability.CREATE,
            TargetCapability.READ_MEMBERS,
            TargetCapability.WRITE_MEMBERS,
            TargetCapability.DELETE,
        ),
    )

    private val targets = ConcurrentHashMap<String, ExternalTarget>()
    private val members = ConcurrentHashMap<Pair<String, String>, String>()
    private val ids = AtomicLong(9000)

    override fun catalog(query: String?): List<ExternalTarget> =
        targets.values.filter { it.matches(query.orEmpty().trim().lowercase()) }

    override fun resolve(externalId: String): ExternalTarget? = targets[externalId]

    override fun create(label: String, folder: String?): ExternalTarget {
        val target = ExternalTarget(
            system,
            ids.getAndIncrement().toString(),
            descriptor.kind,
            label,
            folder,
            path = listOfNotNull(descriptor.systemLabel, folder?.takeIf { it.isNotBlank() }),
        )
        targets[target.externalId] = target
        return target
    }

    override fun members(target: ExternalTarget): List<ExternalMember> =
        members.entries
            .filter { it.key.second == target.externalId }
            .map { ExternalMember(it.key.first, it.value.ifBlank { null }) }

    override fun add(target: ExternalTarget, externalUserId: String) {
        members[externalUserId to target.externalId] = ""
    }

    override fun remove(target: ExternalTarget, externalUserId: String) {
        members.remove(externalUserId to target.externalId)
    }

    override fun delete(target: ExternalTarget) {
        targets.remove(target.externalId)
        members.keys.removeIf { it.second == target.externalId }
    }

    private fun ExternalTarget.matches(query: String): Boolean =
        query.isBlank() ||
            externalId == query ||
            label.lowercase().contains(query) ||
            folderLabel.orEmpty().lowercase().contains(query)
}
