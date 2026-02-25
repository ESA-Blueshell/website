package net.blueshell.api.domain.user.persistence.spec

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import net.blueshell.api.domain.user.application.query.MemberProfileLifecycleQuery
import net.blueshell.api.domain.user.persistence.lifecycle.LifecycleSoftDeleteTimestamps
import net.blueshell.api.domain.user.persistence.lifecycle.MemberProfileLifecycle
import org.springframework.data.jpa.domain.Specification
import java.time.Instant

object MemberProfileLifecycleSpecifications {
    private fun hasUserId(userId: Long): Specification<MemberProfileLifecycle> {
        return Specification { root, _, cb ->
            cb.equal(root.get<Any>("id"), userId)
        }
    }

    private fun hasUserIds(userIds: Set<Long>): Specification<MemberProfileLifecycle> {
        return Specification { root, _, _ ->
            root.get<Long>("id").`in`(userIds)
        }
    }

    private fun softDeleted(softDeleted: Boolean): Specification<MemberProfileLifecycle> {
        return Specification { root, _, cb ->
            val activeDeletedAt = LifecycleSoftDeleteTimestamps.ACTIVE_ROW_DELETED_AT
            if (softDeleted) {
                cb.notEqual(root.get<Instant>("deletedAt"), activeDeletedAt)
            } else {
                cb.equal(root.get<Instant>("deletedAt"), activeDeletedAt)
            }
        }
    }

    fun fromQuery(query: MemberProfileLifecycleQuery): Specification<MemberProfileLifecycle> {
        var spec = Specification { _: Root<MemberProfileLifecycle>, _: CriteriaQuery<*>?, cb: CriteriaBuilder -> cb.conjunction() }

        query.userId?.let {
            spec = spec.and(hasUserId(it))
        }
        query.userIds?.takeIf { it.isNotEmpty() }?.let {
            spec = spec.and(hasUserIds(it))
        }
        query.softDeleted?.let {
            spec = spec.and(softDeleted(it))
        }

        return spec
    }
}
