package net.blueshell.api.base;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ResolvableType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * <h2>Generic CRUD service</h2>
 *
 * <p>A reusable base class that wraps a JPA repository and exposes
 * common CRUD operations. Subclasses supply:</p>
 * <ul>
 *   <li>{@code T}  – the entity type (extends {@link BaseModel})</li>
 *   <li>{@code ID} – the entity’s primary-key type</li>
 *   <li>{@code R}  – a Spring-Data repository for {@code T}</li>
 * </ul>
 *
 * <p>The internal repository is <b>private</b>, so concrete services cannot call it
 * directly – all persistence goes through the methods defined here.</p>
 *
 * <p>Every data-changing operation calls the corresponding
 * {@code pre…} / {@code post…} hook. Override these in a subclass when you need
 * extra logic (validation, auditing, events, etc.).</p>
 */
@Slf4j
public abstract class BaseModelService<T extends BaseModel, R extends BaseRepository<T>> extends IdentityProvider {

    protected final R repository;

    private final String entityLabel;

    @PersistenceContext
    private EntityManager em;

    protected BaseModelService(R repository) {
        this.repository = repository;
        // Try to resolve T from the repository generic type for a nice label like "User"
        Class<?> resolved = ResolvableType.forClass(repository.getClass()).as(BaseRepository.class).getGeneric(0).resolve();
        this.entityLabel = resolved != null ? resolved.getSimpleName() : "Resource";
    }

    /* -----------------------------------------------------------------
     * CREATE
     * ----------------------------------------------------------------- */

    /**
     * Save a new entity.
     * <p>Sequence:&nbsp;
     * {@code preCreate → save&flush → refresh → postCreate}</p>
     */
    @Transactional
    public T create(T entity) {
        entity = repository.saveAndFlush(entity);
        em.refresh(entity);
        return entity;
    }


    /**
     * Create a collection of entities.
     * <p>Each element is processed individually, so every item still triggers
     * the pre/post hooks and id-existence check.</p>
     */
    @Transactional
    public List<T> createAll(List<T> entities) {
        return entities.stream().map(this::create).toList();
    }


    /* -----------------------------------------------------------------
     * UPDATE (single + batch)
     * ----------------------------------------------------------------- */

    /**
     * Update an existing entity.
     * <p>Throws {@link ResponseStatusException} if the id is unknown.</p>
     */
    @Transactional
    public T update(T entity) {
        var id = entity.getId();
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "%s not found with id: %s".formatted(entityLabel, id));
        }
        entity = repository.saveAndFlush(entity);
        em.refresh(entity);
        return entity;
    }

    /**
     * Update a collection of entities.
     * <p>Each element is processed individually, so every item still triggers
     * the pre/post hooks and id-existence check.</p>
     */
    @Transactional
    public List<T> updateAll(List<T> entities) {
        return entities.stream().map(this::update).toList();
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
    public T findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "%s not found with id: %s".formatted(entityLabel, id)));
    }

    /**
     * Return <em>all</em> records.
     */
    @Transactional(readOnly = true)
    public List<T> findAll() {
        return repository.findAll();
    }

    /**
     * Return a paged slice, preserving any sort supplied in the pageable.
     */
    @Transactional(readOnly = true)
    public Page<T> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    /**
     * Fetch the entities whose ids are in {@code ids}.
     * <p>The order of the returned list is the repository’s default (usually
     * primary-key order).</p>
     */
    @Transactional(readOnly = true)
    public List<T> findAllById(List<Long> ids) {
        return repository.findAllById(ids);
    }

    /**
     * Find by specification &amp; paging.
     * The repository must implement {@code JpaSpecificationExecutor}.
     */
    @Transactional(readOnly = true)
    public Page<T> findAll(Specification<T> spec, Pageable pageable) {
        if (!(repository instanceof org.springframework.data.jpa.repository.JpaSpecificationExecutor<?> specRepo)) {
            throw new UnsupportedOperationException("Repository does not support Specifications");
        }
        @SuppressWarnings("unchecked") var result = ((org.springframework.data.jpa.repository.JpaSpecificationExecutor<T>) specRepo).findAll(spec, pageable);
        return result;
    }

    /* -----------------------------------------------------------------
     * DELETE
     * ----------------------------------------------------------------- */

    /**
     * Delete the entity with the given primary key
     */
    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public void deleteAllById(Set<Long> ids) {
        repository.deleteAllByIdInBatch(ids);
    }

    /**
     * Delete the given entity instance
     */
    @Transactional
    public void delete(T entity) {
        repository.delete(entity);
    }

    @Transactional
    public void deleteAll(Set<T> entities) {
        repository.deleteAllInBatch(entities);
    }
}
