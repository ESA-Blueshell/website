package net.blueshell.api.jobs.persistence

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import net.blueshell.api.user.persistence.User
import net.blueshell.api.jobs.domain.JobExecutionQuery
import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.JobExecutionCategory
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

    fun category(category: JobExecutionCategory?): Specification<JobExecution> {
        if (category == null) {
            return Specification { _, _, cb -> cb.conjunction() }
        }
        return Specification { root, _, cb ->
            val jobType = cb.lower(root.get<String>("jobType"))
            val calendar = categoryPrefix(jobType, cb, JobExecutionCategory.calendar.name)
            val contact = categoryPrefix(jobType, cb, JobExecutionCategory.contact.name)
            val cohort = categoryPrefix(jobType, cb, JobExecutionCategory.cohort.name)
            val email = categoryPrefix(jobType, cb, JobExecutionCategory.email.name)

            when (category) {
                JobExecutionCategory.calendar -> calendar
                JobExecutionCategory.contact -> contact
                JobExecutionCategory.cohort -> cohort
                JobExecutionCategory.email -> email
                JobExecutionCategory.other -> cb.and(
                    cb.not(calendar),
                    cb.not(contact),
                    cb.not(cohort),
                    cb.not(email)
                )
            }
        }
    }

    fun jobTypeContains(value: String?): Specification<JobExecution> {
        val normalized = value?.trim()?.lowercase(Locale.getDefault())?.takeIf { it.isNotBlank() }
            ?: return Specification { _, _, cb -> cb.conjunction() }
        return contains("jobType", normalized)
    }

    fun search(value: String?): Specification<JobExecution> {
        val raw = value?.trim()?.takeIf { it.isNotBlank() }
            ?: return Specification { _, _, cb -> cb.conjunction() }
        val normalized = raw.lowercase(Locale.getDefault())

        var spec = contains("jobType", normalized)
            .or(contains("errorType", normalized))
            .or(containsLargeText("errorMessage", raw))
            .or(containsLargeText("errorReason", raw))
            .or(containsLargeText("payload", raw))
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
        filter.category?.let { spec = spec.and(category(it)) }
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

    private fun categoryPrefix(
        jobType: jakarta.persistence.criteria.Expression<String>,
        cb: CriteriaBuilder,
        prefix: String
    ) = cb.or(
        cb.equal(jobType, prefix),
        cb.like(jobType, "$prefix.%"),
        cb.like(jobType, "${prefix}_%"),
        cb.like(jobType, "${prefix}-%")
    )

    private fun containsLargeText(fieldName: String, value: String): Specification<JobExecution> {
        val variants = linkedSetOf(
            value,
            value.lowercase(Locale.getDefault()),
            value.uppercase(Locale.getDefault())
        )
        return Specification { root, _, cb ->
            val field = root.get<String>(fieldName)
            cb.or(*variants.map { variant ->
                cb.like(field, "%$variant%")
            }.toTypedArray())
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
            val subquery = query.subquery(Long::class.java)
            val user = subquery.from(User::class.java)
            val firstName = user.get<String>("firstName")
            val prefix = cb.coalesce(user.get<String>("prefix"), "")
            val lastName = user.get<String>("lastName")
            val fullName = cb.lower(
                cb.concat(
                    cb.concat(firstName, cb.literal(" ")),
                    lastName
                )
            )
            val fullNameWithPrefix = cb.lower(
                cb.concat(
                    cb.concat(
                        cb.concat(firstName, cb.literal(" ")),
                        cb.concat(prefix, cb.literal(" "))
                    ),
                    lastName
                )
            )
            subquery.select(cb.literal(1L))
                .where(
                    cb.equal(user.get<Long>("id"), root.get<Long>("initiatedByUserId")),
                    cb.or(
                        cb.like(cb.lower(user.get("username")), pattern),
                        cb.like(cb.lower(firstName), pattern),
                        cb.like(cb.lower(lastName), pattern),
                        cb.like(fullName, pattern),
                        cb.like(fullNameWithPrefix, pattern)
                    )
                )

            cb.exists(subquery)
        }
    }
}
