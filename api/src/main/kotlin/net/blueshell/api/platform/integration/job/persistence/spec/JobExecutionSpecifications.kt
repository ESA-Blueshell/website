package net.blueshell.api.platform.integration.job.persistence.spec

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.job.application.query.JobExecutionQuery
import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.JobExecutionStatus
import org.springframework.data.jpa.domain.Specification
import java.util.Locale

object JobExecutionSpecifications {
    fun status(status: JobExecutionStatus?): Specification<JobExecution> {
        if (status == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb -> cb.equal(root.get<JobExecutionStatus>("status"), status) }
    }

    fun initiatedByType(type: ActionActorType?): Specification<JobExecution> {
        if (type == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb -> cb.equal(root.get<ActionActorType>("initiatedByType"), type) }
    }

    fun category(category: String?): Specification<JobExecution> {
        val normalized = category?.trim()?.lowercase(Locale.getDefault())?.takeIf { it.isNotBlank() }
            ?: return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            val jobType = cb.lower(root.get<String>("jobType"))
            cb.or(
                cb.equal(jobType, normalized),
                cb.like(jobType, "${normalized}.%"),
                cb.like(jobType, "${normalized}_%"),
                cb.like(jobType, "${normalized}-%")
            )
        }
    }

    fun jobTypeContains(value: String?): Specification<JobExecution> {
        val normalized = value?.trim()?.lowercase(Locale.getDefault())?.takeIf { it.isNotBlank() }
            ?: return Specification { _, _, cb -> cb.conjunction() }
        return contains("jobType", normalized)
    }

    fun search(value: String?): Specification<JobExecution> {
        val normalized = value?.trim()?.lowercase(Locale.getDefault())?.takeIf { it.isNotBlank() }
            ?: return Specification { _, _, cb -> cb.conjunction() }

        var spec = contains("jobType", normalized)
            .or(contains("errorType", normalized))
            .or(contains("errorMessage", normalized))
            .or(contains("errorReason", normalized))
            .or(contains("payload", normalized))
            .or(initiatedByUserMatches(normalized))

        normalized.toLongOrNull()?.let { userId ->
            spec = spec.or(initiatedByUserId(userId))
        }

        return spec
    }

    fun fromFilter(filter: JobExecutionQuery): Specification<JobExecution> {
        var spec = Specification { _: Root<JobExecution>, _: CriteriaQuery<*>?, cb: CriteriaBuilder -> cb.conjunction() }

        filter.status?.let { spec = spec.and(status(it)) }
        filter.initiatedByType?.let { spec = spec.and(initiatedByType(it)) }
        if (!filter.category.isNullOrBlank()) {
            spec = spec.and(category(filter.category))
        }
        if (!filter.jobType.isNullOrBlank()) {
            spec = spec.and(jobTypeContains(filter.jobType))
        }
        if (!filter.search.isNullOrBlank()) {
            spec = spec.and(search(filter.search))
        }

        return spec
    }

    private fun contains(fieldName: String, normalized: String): Specification<JobExecution> {
        val pattern = "%$normalized%"
        return Specification { root, _, cb ->
            cb.like(cb.lower(root.get<String>(fieldName)), pattern)
        }
    }

    private fun initiatedByUserId(userId: Long): Specification<JobExecution> {
        return Specification { root, _, cb ->
            cb.equal(root.get<Long>("initiatedByUserId"), userId)
        }
    }

    private fun initiatedByUserMatches(normalized: String): Specification<JobExecution> {
        val pattern = "%$normalized%"
        return Specification { root, query, cb ->
            if (query == null) {
                return@Specification cb.disjunction()
            }

            val subquery = query.subquery(Long::class.java)
            val user = subquery.from(User::class.java)
            subquery.select(cb.literal(1L))
                .where(
                    cb.equal(user.get<Long>("id"), root.get<Long>("initiatedByUserId")),
                    cb.or(
                        cb.like(cb.lower(user.get("username")), pattern),
                        cb.like(cb.lower(user.get("fullName")), pattern)
                    )
                )

            cb.exists(subquery)
        }
    }
}
