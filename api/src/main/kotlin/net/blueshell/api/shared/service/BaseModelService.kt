package net.blueshell.api.shared.service

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.feature.auth.security.IdentityProvider
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
 * <h2>Generic CRUD service</h2>
 *
 *
 * A reusable base class that wraps a JPA repository and exposes
 * common CRUD operations. Subclasses supply:
 *
 *  * `T`  – the entity type (implements [Identifiable])
 *  * `ID` – the entity’s primary-key type
 *  * `R`  – a Spring-Data repository for `T`
 *
 *
 *
 * The internal repository is **private**, so concrete services cannot call it
 * directly – all persistence goes through the methods defined here.
 *
 *
 * Every data-changing operation calls the corresponding
 * `pre…` / `post…` hook. Override these in a subclass when you need
 * extra logic (validation, auditing, events, etc.).
 */
abstract class BaseModelService<T : Identifiable<ID>, ID, R : BaseRepository<T, ID>>(protected val repository: R) :
    IdentityProvider() {
    private val entityLabel: String

    @PersistenceContext
    private lateinit var em: EntityManager

    init {
        // Try to resolve T from the repository generic type for a nice label like "User"
        val resolved =
            ResolvableType.forClass(repository.javaClass).`as`(BaseRepository::class.java).getGeneric(0).resolve()
        this.entityLabel = if (resolved != null) resolved.simpleName else "Resource"
    }

    /* -----------------------------------------------------------------
     * CREATE
     * ----------------------------------------------------------------- */
    /**
     * Save a new entity.
     *
     * Sequence:&nbsp;
     * `preCreate → save&flush → refresh → postCreate`
     */
    @Transactional
    open fun create(entity: T): T {
        var entity = entity
        entity = repository.saveAndFlush(entity)
        em.refresh(entity)
        return entity
    }


    /**
     * Create a collection of entities.
     *
     * Each element is processed individually, so every item still triggers
     * the pre- / post-hooks and id-existence check.
     */
    @Transactional
    open fun createAll(entities: MutableList<T>): MutableList<T> {
        return entities.map { this.create(it) }.toMutableList()
    }


    /* -----------------------------------------------------------------
     * UPDATE (single + batch)
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

    /**
     * Update a collection of entities.
     *
     * Each element is processed individually, so every item still triggers
     * the pre- / post-hooks and id-existence check.
     */
    @Transactional
    open fun updateAll(entities: MutableList<T>): MutableList<T> {
        return entities.map { this.update(it) }.toMutableList()
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
        return repository.findById(id).orElseThrow(Supplier {
            ResponseStatusException(HttpStatus.NOT_FOUND, "$entityLabel not found with id: $id")
        })
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
    open fun findAll(specification: Specification<T>, pageable: Pageable): Page<T> =
        repository.findAll(specification, pageable)

    /* -----------------------------------------------------------------
     * DELETE
     * ----------------------------------------------------------------- */
    /**
     * Delete an entity by its primary key.
     */
    @Transactional
    open fun deleteById(id: ID) {
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

    /**
     * Delete all entities by their ids.
     */
    @Transactional
    open fun deleteAllById(ids: Set<ID>) {
        repository.deleteAllById(ids)
    }
}