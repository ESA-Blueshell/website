package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortSubject
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.shared.enums.TargetSystem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Keeps the cohort records in step with the definitions in code, one record per definition.
 *
 * Runs when something could have changed the set of definitions — a period or committee
 * created, a reconcile asked for — and never on startup: writing during boot turns a schema
 * surprise into a boot failure an operator cannot opt out of. A record whose definition is gone
 * is reported as orphaned rather than deleted: its external
 * list may still be wanted, and that is an operator's call.
 */
@Service
class CohortRegistrar(
    private val definitions: CohortDefinitionRegistry,
    private val subjects: CohortSubjectRepository,
    private val cohorts: CohortRepository,
    private val strategies: TargetStrategies,
) {
    @Transactional
    fun register(): RegistrationReport {
        val all = definitions.all()
        val byKey = subjects.findAll().associateBy { it.definitionKey }

        var created = 0
        var relabelled = 0
        all.forEach { definition ->
            val existing = byKey[definition.key]
            if (existing == null) {
                createFor(definition)
                created += 1
                return@forEach
            }
            // A committee that renames itself renames its cohort; nothing else about the
            // record is the definition's to change.
            if (existing.label != definition.label) {
                existing.label = definition.label
                subjects.save(existing)
                relabelled += 1
            }
        }

        val known = all.mapTo(mutableSetOf()) { it.key }
        val orphaned = byKey.keys.filterNotNull().filterNot { it in known }
        if (orphaned.isNotEmpty()) {
            log.info("[cohort] {} cohort(s) have no definition any more: {}", orphaned.size, orphaned)
        }
        log.info("[cohort] registered {} definitions ({} new, {} relabelled)", all.size, created, relabelled)
        return RegistrationReport(total = all.size, created = created, relabelled = relabelled, orphaned = orphaned)
    }

    private fun createFor(definition: CohortDefinition) {
        val subject = subjects.save(
            CohortSubject(
                type = definition.type,
                label = definition.label,
                definitionKey = definition.key,
            ),
        )
        // One target per system the association syncs to. Only the target's id is missing,
        // and an operator supplies that by creating or linking the list itself.
        val system = TargetSystem.BREVO
        if (cohorts.findBySubjectIdAndSystem(subject.id!!, system.name) == null) {
            cohorts.save(
                Cohort(
                    system = system.name,
                    kind = strategies.descriptor(system).kind,
                    label = definition.label,
                    folder = definition.folder,
                    subjectId = subject.id,
                ),
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortRegistrar::class.java)
    }
}

data class RegistrationReport(
    val total: Int,
    val created: Int,
    val relabelled: Int,
    val orphaned: List<String>,
)
