package net.blueshell.api.base

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
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
 *  * `T`  – the entity type (extends [BaseModel])
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
abstract class BaseModelService<T : BaseModel, R : BaseRepository<T>>(protected val repository: R) :
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
        entity = repository.saveAndFlush<T>(entity)
        em.refresh(entity)
        return entity
    }


    /**
     * Create a collection of entities.
     *
     * Each element is processed individually, so every item still triggers
     * the pre/post hooks and id-existence check.
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
     * Throws [ResponseStatusException] if the id is unknown.
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
     * the pre/post hooks and id-existence check.
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
    open fun findById(id: Long?): T {
        return repository.findById(id).orElseThrow(Supplier {
            ResponseStatusException(
                HttpStatus.NOT_FOUND, "$entityLabel not found with id: $id"
            )
        })
    }

    /**
     * Return *all* records.
     */
    @Transactional(readOnly = true)
    open fun findAll(): MutableList<T> {
        return repository.findAll()
    }

    /**
     * Return a paged slice, preserving any sort supplied in the pageable.
     */
    @Transactional(readOnly = true)
    open fun findAll(pageable: Pageable): Page<T> {
        return repository.findAll(pageable).map { it!! }
    }

    /**
     * Fetch the entities whose ids are in `ids`.
     *
     * The order of the returned list is the repository’s default (usually
     * primary-key order).
     */
    @Transactional(readOnly = true)
    open fun findAllById(ids: MutableList<Long>): MutableList<T> {
        return repository.findAllById(ids).filterNotNull().toMutableList()
    }

    /**
     * Find by specification &amp; paging.
     * The repository must implement `JpaSpecificationExecutor`.
     */
    @Transactional(readOnly = true)
    open fun findAll(spec: Specification<T>, pageable: Pageable): Page<T> {
        return repository.findAll(spec, pageable)
    }

    /* -----------------------------------------------------------------
     * DELETE
     * ----------------------------------------------------------------- */
    /**
     * Delete the entity with the given primary key
     */
    @Transactional
    open fun deleteById(id: Long) {
        repository.deleteById(id)
    }

    @Transactional
    open fun deleteAllById(ids: MutableSet<Long>) {
        repository.deleteAllByIdInBatch(ids)
    }

    /**
     * Delete the given entity instance
     */
    @Transactional
    open fun delete(entity: T) {
        repository.delete(entity)
    }

    @Transactional
    open fun deleteAll(entities: MutableSet<T>) {
        repository.deleteAllInBatch(entities)
    }
}
