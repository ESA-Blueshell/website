package net.blueshell.api.shared.service

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.shared.model.Identifiable
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.core.ResolvableType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.function.Supplier

/**
 * Generic CRUD over a JPA repository for entity [T] with key [ID] and repository [R].
 *
 * The repository is protected, so a subclass reaches it directly for its own queries. A missing
 * row answers 404 through a `ResponseStatusException` naming the entity, unless the subclass
 * overrides [findById] with its own refusal. Board folds its service into its use cases instead,
 * and architecture ADR-002 says the rest follow.
 */
abstract class BaseModelService<T : Identifiable<ID>, ID : Any, R : BaseRepository<T, ID>>(
    protected val repository: R,
) {
    private val entityLabel: String

    @PersistenceContext
    private lateinit var em: EntityManager

    init {
        // Try to resolve T from the repository generic type for a nice label like "User"
        val resolved =
            ResolvableType
                .forClass(repository.javaClass)
                .`as`(BaseRepository::class.java)
                .getGeneric(0)
                .resolve()
        this.entityLabel = if (resolved != null) resolved.simpleName else "Resource"
    }

    /* -----------------------------------------------------------------
     * CREATE
     * ----------------------------------------------------------------- */

    /** Saves a new entity, then reads it back so the columns the database fills are set. */
    @Transactional
    open fun create(entity: T): T {
        var entity = entity
        entity = repository.saveAndFlush(entity)
        em.refresh(entity)
        return entity
    }

    /* -----------------------------------------------------------------
     * UPDATE
     * ----------------------------------------------------------------- */

    /**
     * Update an existing entity.
     *
     * Throws [org.springframework.web.server.ResponseStatusException] if the id is unknown.
     */
    @Transactional
    open fun update(entity: T): T {
        var entity = entity
        val id = entity.id
        if (id == null || !repository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "$entityLabel not found with id: $id")
        }
        entity = repository.saveAndFlush(entity)
        em.refresh(entity)
        return entity
    }

    /* -----------------------------------------------------------------
     * READ
     * ----------------------------------------------------------------- */

    /**
     * Find one by its primary key.
     *
     * @throws ResponseStatusException (404) if not present
     */
    @Transactional(readOnly = true)
    open fun findById(id: ID): T {
        @Suppress("UNCHECKED_CAST")
        return repository.findById(id).orElseThrow(
            Supplier {
                ResponseStatusException(HttpStatus.NOT_FOUND, "$entityLabel not found with id: $id")
            },
        )
    }

    /**
     * Find all entities.
     */
    @Transactional(readOnly = true)
    open fun findAll(): MutableList<T> = repository.findAll()

    /**
     * Find all entities with pagination.
     */
    @Transactional(readOnly = true)
    open fun findAll(pageable: Pageable): Page<T> = repository.findAll(pageable)

    /**
     * Find all entities matching a specification.
     */
    @Transactional(readOnly = true)
    open fun findAll(specification: Specification<T>): MutableList<T> = repository.findAll(specification)

    /**
     * Find all entities matching a specification with pagination.
     */
    @Transactional(readOnly = true)
    open fun findAll(
        specification: Specification<T>,
        pageable: Pageable,
    ): Page<T> = repository.findAll(specification, pageable)

    /* -----------------------------------------------------------------
     * DELETE
     * ----------------------------------------------------------------- */

    /**
     * Delete an entity by its primary key.
     */
    @Transactional
    open fun deleteById(id: ID) {
        @Suppress("UNCHECKED_CAST")
        if (!repository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "$entityLabel not found with id: $id")
        }
        repository.deleteById(id)
    }

    /**
     * Delete an entity.
     */
    @Transactional
    open fun delete(entity: T) {
        repository.delete(entity)
    }

    /**
     * Delete all entities.
     */
    @Transactional
    open fun deleteAll(entities: Set<T>) {
        repository.deleteAll(entities)
    }

    /* -----------------------------------------------------------------
     * OTHER
     * ----------------------------------------------------------------- */

    @Transactional
    open fun existsById(id: ID): Boolean = repository.existsById(id)
}
