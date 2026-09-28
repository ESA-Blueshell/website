package net.blueshell.api.jobs.persistence

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import java.time.Instant

/** A trigger that met this job already queued, and was folded into it rather than queued again. */
data class FoldedTrigger(
    val trigger: JobTrigger,
    val actor: Actor,
    val at: Instant,
)

/**
 * Folded triggers as `TRIGGER,ACTOR_TYPE,ROLE,USER_ID,INSTANT` entries joined by `;`, the user id
 * empty for the system. Kept on the row rather than in a table of their own, so a page of jobs
 * costs no query per row.
 */
@Converter
class FoldedTriggersConverter : AttributeConverter<List<FoldedTrigger>?, String?> {
    override fun convertToDatabaseColumn(attribute: List<FoldedTrigger>?): String? =
        attribute
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(";") { folded ->
                val userId = folded.actor.userId ?: ""
                "${folded.trigger},${folded.actor.type},${folded.actor.role},$userId,${folded.at}"
            }

    override fun convertToEntityAttribute(dbData: String?): List<FoldedTrigger> =
        dbData.orEmpty().split(";").filter { it.isNotBlank() }.map { entry ->
            val (trigger, type, rest) = entry.split(",", limit = 3)
            val (role, userId, at) = rest.split(",")
            FoldedTrigger(
                trigger = JobTrigger.valueOf(trigger),
                actor = Actor(userId.toLongOrNull(), ActionActorType.valueOf(type), Role.valueOf(role)),
                at = Instant.parse(at),
            )
        }
}
