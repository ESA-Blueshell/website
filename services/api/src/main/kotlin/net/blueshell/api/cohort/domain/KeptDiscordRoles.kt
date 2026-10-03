package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * The server's existing roles that follow a cohort, named in `discord.cohort-roles` by the cohort's
 * definition key: the Member role follows the members cohort, the Activist role the activists. Each
 * is linked as its cohort's Discord target the first time the cohort is registered with one named,
 * and reconciled then. A cohort already linked to a role keeps it; switching is an admin's act.
 */
@Component
class KeptDiscordRoles(
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val targetIds: CohortTargetIds,
    private val jobs: JobQueue,
    environment: Environment,
) {
    private val roleByKey: Map<String, String> =
        Binder
            .get(environment)
            .bind("discord.cohort-roles", Bindable.mapOf(String::class.java, String::class.java))
            .orElse(emptyMap())
            .orEmpty()
            .mapValues { it.value.trim() }
            .filterValues { it.isNotEmpty() }

    /** Links each named role to its cohort where that cohort has no Discord target yet; answers how many. */
    fun link(): Int {
        var linked = 0
        roleByKey.forEach { (key, roleId) ->
            val cohort = cohorts.findByDefinitionKey(key) ?: return@forEach
            val existing = targets.findByCohortIdAndSystem(requireNotNull(cohort.id), TargetSystem.DISCORD.name)
            if (existing != null && targetIds.find(existing) != null) return@forEach
            val target =
                existing ?: targets.save(Target(TargetSystem.DISCORD.name, TargetKind.ROLE, cohort.label, cohortId = cohort.id))
            targetIds.record(target, roleId)
            jobs.reconcileTarget(requireNotNull(target.id), JobTrigger.ANOTHER_JOB)
            log.info("[cohort] linked Discord role {} to cohort {}", roleId, key)
            linked += 1
        }
        return linked
    }

    private companion object {
        val log = LoggerFactory.getLogger(KeptDiscordRoles::class.java)
    }
}
