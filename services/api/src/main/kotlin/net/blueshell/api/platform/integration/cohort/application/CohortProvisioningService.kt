package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Ensures the subject and its per-system cohort exist for one `(factKind,
 * factKey)` rule, in two steps rather than the retired three (subject →
 * cohort → rule). The resolvers are now spec builders over this one service.
 *
 * A disabled subject is never bypassed: an admin who turned a subject off is
 * not undone by manufacturing a fresh enabled subject for the same fact pair.
 */
@Service
class CohortProvisioningService(
    private val subjects: CohortSubjectRepository,
    private val cohorts: CohortRepository,
    private val registry: CohortPortRegistry,
) {
    @Transactional
    fun provision(spec: CohortProvisioningSpec): CohortProvisioningResult {
        val existing = subjects.findByFactKindAndFactKey(spec.factKind, spec.factKey)
        if (existing != null && !existing.enabled) {
            return CohortProvisioningResult.Disabled(existing.id!!)
        }
        val subject = existing ?: subjects.save(
            CohortSubject(
                type = spec.subjectType,
                label = spec.label,
                factKind = spec.factKind,
                factKey = spec.factKey,
                enabled = true,
            ),
        )
        val cohort = cohorts.findBySubjectIdAndSystem(subject.id!!, spec.system.name)
            ?: cohorts.save(
                Cohort(
                    system = spec.system.name,
                    kind = registry.require(spec.system).kind,
                    label = spec.label,
                    folder = spec.folder,
                    subjectId = subject.id,
                ),
            )
        return CohortProvisioningResult.Ready(cohort)
    }
}

/**
 * What to provision: the rule's `(factKind, factKey)`, the subject [subjectType]
 * to create if missing, and the [system] cohort's display [label] / [folder].
 * `CUSTOM` aside, the resolvers fill this from code-defined parts.
 */
data class CohortProvisioningSpec(
    val factKind: CohortFactKind,
    val factKey: String,
    val subjectType: CohortSubjectType,
    val system: TargetSystem = TargetSystem.BREVO,
    val label: String,
    val folder: String?,
)

sealed interface CohortProvisioningResult {
    /** The subject's cohort on the requested system, created or already present. */
    data class Ready(val cohort: Cohort) : CohortProvisioningResult

    /** The subject for this fact pair exists but is disabled; nothing was provisioned. */
    data class Disabled(val subjectId: Long) : CohortProvisioningResult
}
