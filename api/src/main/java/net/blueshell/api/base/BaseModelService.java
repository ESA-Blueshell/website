package net.blueshell.api.base;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.exception.ResourceNotFoundException;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
public abstract class BaseModelService<
        T extends BaseModel,
        R extends BaseRepository<T>>
        extends IdentityProvider {

    protected final R repository;

    @PersistenceContext
    private EntityManager em;

    protected BaseModelService(R repository) {
        this.repository = repository;
    }

    @SuppressWarnings("unchecked")
    protected BaseModelService<T, R> self() {
        return (BaseModelService<T, R>) AopContext.currentProxy();
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
    public void create(T entity) {
        repository.saveAndFlush(entity);
        em.refresh(entity);
    }

    /* -----------------------------------------------------------------
     * UPDATE (single + batch)
     * ----------------------------------------------------------------- */

    /**
     * Update an existing entity.
     * <p>Throws {@link ResourceNotFoundException} if the id is unknown.</p>
     */
    @Transactional
    public void update(T entity) {
        var id = entity.getId();
        if (id == null || !repository.existsById(id)) {
            throw new ResourceNotFoundException("Entity not found with id: %s".formatted(id));
        }
        repository.saveAndFlush(entity);
        log.info("Entity: {}", entity);
        em.refresh(entity);
    }

    /**
     * Update a collection of entities.
     * <p>Each element is processed individually, so every item still triggers
     * the pre/post hooks and id-existence check.</p>
     */
    @Transactional
    public void updateAll(List<T> entities) {
        entities.forEach(this::update);
    }

    /* -----------------------------------------------------------------
     * READ
     * ----------------------------------------------------------------- */

    /**
     * Find one by its primary key.
     *
     * @throws ResourceNotFoundException if not present
     */
    @Transactional(readOnly = true)
    public T findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Resource not found with id: " + id));
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
        @SuppressWarnings("unchecked")
        var result = ((org.springframework.data.jpa.repository.JpaSpecificationExecutor<T>) specRepo)
                .findAll(spec, pageable);
        return result;
    }

    /* -----------------------------------------------------------------
     * DELETE
     * ----------------------------------------------------------------- */

    /**
     * Delete the entity with the given primary key
     */
    @Transactional
    public void delete(Long id) {
        var entity = self().findById(id);
        self().delete(entity);
    }

    /**
     * Delete the given entity instance
     */
    @Transactional
    public void delete(T entity) {
        repository.delete(entity);
    }
}
