package net.blueshell.api.jobs.web

/** One entity a job payload names, as the payload field carrying it and its id. */
data class JobSubject(val field: String, val id: Long)

/**
 * Describes an entity a job payload refers to, for the Job Manager to show.
 *
 * Implemented by the module that owns the entity, so the job module never
 * imports another module's services or entities to build a label. Resolvers are
 * collected by bean type and applied in `@Order`, which fixes the order the
 * related entities appear in.
 */
interface JobSubjectResolver {
    /** Payload fields that carry this subject's id. The first one present wins. */
    val payloadFields: List<String>

    /** The related-entity type the Job Manager groups on, such as `EVENT`. */
    val entityType: String

    /**
     * A label for this id, including the fallback used when the entity is gone —
     * a job outlives what it acted on, so this must never fail.
     */
    fun label(id: Long): String

    /** Further subjects this one implies, resolved by their own resolvers. */
    fun implied(id: Long): List<JobSubject> = emptyList()
}
