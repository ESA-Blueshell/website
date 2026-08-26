package net.blueshell.api.platform.integration.cohort.application.definition

import net.blueshell.api.platform.integration.cohort.application.TargetStrategies
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.shared.enums.TargetSystem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Keeps the cohort records in step with the definitions in code.
 *
 * A definition is the truth about what a cohort is; the record exists so that a cohort can be
 * linked to a target on an external system and can hold a membership ledger. This puts one
 * record behind each definition and leaves the rest alone.
 *
 * It runs when something happens that could have changed the set of definitions — a period
 * created, a committee created, a reconcile asked for — and never on startup: writing to the
 * database while the application is still coming up turns a schema surprise into a boot
 * failure, and gives an operator no way to not run it.
 *
 * A record whose definition no longer exists is not deleted. Its external list may still be
 * wanted, and deciding that is an operator's call, so it is reported as orphaned instead.
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
