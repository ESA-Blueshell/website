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
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory [TargetStrategy] for the test and dev profiles: the registry, the remediation
 * services and the controller ITs get a real target to exercise without touching Brevo.
 *
 * Reported system is [TargetSystem.BREVO] so those paths run as they do in production.
 */
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

    /**
     * `isActualTransactionActive()` at the entry of every provider call, so a boundary test can
     * assert these run outside any DB transaction (the no-provider-call-inside-a-transaction rule).
     */
    val transactionActiveDuringCalls: MutableList<Boolean> = CopyOnWriteArrayList()

    override fun catalog(query: String?): List<ExternalTarget> {
        recordTransactionState()
        return targets.values.filter { it.matches(query.orEmpty().trim().lowercase()) }
    }

    override fun resolve(externalId: String): ExternalTarget? = targets[externalId]

    override fun create(label: String, folder: String?): ExternalTarget {
        recordTransactionState()
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

    override fun members(target: ExternalTarget): List<ExternalMember> {
        recordTransactionState()
        return members.entries
            .filter { it.key.second == target.externalId }
            .map { ExternalMember(it.key.first, it.value.ifBlank { null }) }
    }

    override fun add(target: ExternalTarget, externalUserId: String) {
        recordTransactionState()
        members[externalUserId to target.externalId] = ""
    }

    override fun remove(target: ExternalTarget, externalUserId: String) {
        recordTransactionState()
        members.remove(externalUserId to target.externalId)
    }

    override fun delete(target: ExternalTarget) {
        recordTransactionState()
        targets.remove(target.externalId)
        members.keys.removeIf { it.second == target.externalId }
    }

    /** Directly seeds a member with an optional label, for the drift tests. */
    fun seedMember(externalUserId: String, externalTargetId: String, label: String? = null) {
        members[externalUserId to externalTargetId] = label.orEmpty()
    }

    fun clear() {
        targets.clear()
        members.clear()
        transactionActiveDuringCalls.clear()
    }

    private fun recordTransactionState() {
        transactionActiveDuringCalls += TransactionSynchronizationManager.isActualTransactionActive()
    }

    private fun ExternalTarget.matches(query: String): Boolean =
        query.isBlank() ||
            externalId == query ||
            label.lowercase().contains(query) ||
            folderLabel.orEmpty().lowercase().contains(query)
}
