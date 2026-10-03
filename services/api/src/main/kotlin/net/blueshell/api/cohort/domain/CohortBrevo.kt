package net.blueshell.api.cohort.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/** A cohort's Brevo list, as a form shows it: the list it has, and the folder it sits in. */
@Schema(name = "BrevoPlace")
data class BrevoPlace(
    /** Whether Brevo can be asked; nothing below is read without it. */
    val available: Boolean,
    val listId: String?,
    val listName: String?,
    val folder: String?,
)

/** What a form asks of Brevo: an existing list to link, or a new one to make, where the cohort has none. */
data class BrevoChoice(
    val listId: String? = null,
    val createList: Boolean = false,
)

/**
 * A committee's Brevo list, as its form sets it. The list is the cohort's Brevo target, so it
 * follows the cohort's people. A cohort holding a list keeps it; one without is linked to an
 * existing list, or given a new one in the folder the caller names.
 */
@Service
class CohortBrevo(
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val targetIds: CohortTargetIds,
    private val targeting: CohortTargeting,
    private val registrar: CohortRegistrar,
    private val strategies: TargetStrategies,
) {
    fun read(key: String): BrevoPlace {
        val strategy = strategies.find(TargetSystem.BREVO)?.takeIf { it.available() } ?: return BrevoPlace(false, null, null, null)
        val target = targets.findByCohortIdAndSystem(cohortIdOf(key), TargetSystem.BREVO.name)
        val listId = target?.let(targetIds::find) ?: return BrevoPlace(true, null, null, target?.folder)
        val list = strategy.resolve(listId)
        return BrevoPlace(true, listId, list?.label ?: target.label, list?.folderLabel ?: target.folder)
    }

    /** Links or makes the list of the cohort defined by [key], a new list going in [folder]. */
    fun apply(
        key: String,
        choice: BrevoChoice,
        folder: String,
    ): BrevoPlace {
        if (strategies.find(TargetSystem.BREVO)?.available() != true) throw TargetSystemUnavailable(TargetSystem.BREVO)
        val cohortId = cohortIdOf(key)
        val linked = targets.findByCohortIdAndSystem(cohortId, TargetSystem.BREVO.name)?.let(targetIds::find)
        when {
            linked != null -> Unit
            choice.listId != null -> targeting.linkExisting(cohortId, TargetSystem.BREVO, choice.listId)
            choice.createList -> targeting.create(cohortId, TargetSystem.BREVO, labelOf(cohortId), folder)
        }
        return read(key)
    }

    // A record made a moment ago may not have its cohort yet, so one is registered for it, as CohortDiscord does.
    private fun cohortIdOf(key: String): Long {
        val cohort =
            cohorts.findByDefinitionKey(key)
                ?: registrar.register().let { cohorts.findByDefinitionKey(key) }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No cohort is defined as $key")
        return requireNotNull(cohort.id)
    }

    private fun labelOf(cohortId: Long) = cohorts.findById(cohortId).map { it.label }.orElseThrow()
}
