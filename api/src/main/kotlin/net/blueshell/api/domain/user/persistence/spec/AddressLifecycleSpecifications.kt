package net.blueshell.api.domain.user.persistence.spec

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import net.blueshell.api.domain.user.application.query.AddressLifecycleQuery
import net.blueshell.api.domain.user.persistence.lifecycle.AddressLifecycle
import net.blueshell.api.domain.user.persistence.lifecycle.LifecycleSoftDeleteTimestamps
import org.springframework.data.jpa.domain.Specification
import java.time.Instant

object AddressLifecycleSpecifications {
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
            val activeDeletedAt = LifecycleSoftDeleteTimestamps.ACTIVE_ROW_DELETED_AT
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
