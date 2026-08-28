package net.blueshell.api.user.persistence

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import net.blueshell.api.user.domain.AddressLifecycleQuery
import net.blueshell.api.user.domain.ProfileLifecycleQuery
import org.springframework.data.jpa.domain.Specification
import java.time.Instant

object AddressLifecycleSpecs {
    private fun hasId(id: Long): Specification<AddressLifecycle> {
        return Specification { root, _, cb ->
            cb.equal(root.get<Any>("id"), id)
        }
    }

    private fun hasIds(ids: Set<Long>): Specification<AddressLifecycle> {
        return Specification { root, _, _ ->
            root.get<Long>("id").`in`(ids)
        }
    }

    private fun softDeleted(softDeleted: Boolean): Specification<AddressLifecycle> {
        return Specification { root, _, cb ->
            val activeDeletedAt = SoftDeleteSentinels.ACTIVE_ROW_DELETED_AT
            if (softDeleted) {
                cb.notEqual(root.get<Instant>("deletedAt"), activeDeletedAt)
            } else {
                cb.equal(root.get<Instant>("deletedAt"), activeDeletedAt)
            }
        }
    }

    fun fromQuery(query: AddressLifecycleQuery): Specification<AddressLifecycle> {
        var spec = Specification { _: Root<AddressLifecycle>, _: CriteriaQuery<*>?, cb: CriteriaBuilder -> cb.conjunction() }

        query.id?.let {
            spec = spec.and(hasId(it))
        }
        query.ids?.takeIf { it.isNotEmpty() }?.let {
            spec = spec.and(hasIds(it))
        }
        query.softDeleted?.let {
            spec = spec.and(softDeleted(it))
        }

        return spec
    }
}

object ProfileLifecycleSpecs {
    private fun hasUserId(userId: Long): Specification<ProfileLifecycle> {
        return Specification { root, _, cb ->
            cb.equal(root.get<Any>("id"), userId)
        }
    }

    private fun hasUserIds(userIds: Set<Long>): Specification<ProfileLifecycle> {
        return Specification { root, _, _ ->
            root.get<Long>("id").`in`(userIds)
        }
    }

    private fun softDeleted(softDeleted: Boolean): Specification<ProfileLifecycle> {
        return Specification { root, _, cb ->
            val activeDeletedAt = SoftDeleteSentinels.ACTIVE_ROW_DELETED_AT
            if (softDeleted) {
                cb.notEqual(root.get<Instant>("deletedAt"), activeDeletedAt)
            } else {
                cb.equal(root.get<Instant>("deletedAt"), activeDeletedAt)
            }
        }
    }

    fun fromQuery(query: ProfileLifecycleQuery): Specification<ProfileLifecycle> {
        var spec = Specification { _: Root<ProfileLifecycle>, _: CriteriaQuery<*>?, cb: CriteriaBuilder -> cb.conjunction() }

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
