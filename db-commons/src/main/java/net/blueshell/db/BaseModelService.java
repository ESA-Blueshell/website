package net.blueshell.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import net.blueshell.common.exception.ResourceNotFoundException;
import net.blueshell.common.identity.IdentityProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public abstract class BaseModelService<T extends BaseModel<ID>, ID, R extends BaseRepository<T,ID>> extends IdentityProvider {

    @PersistenceContext
    private EntityManager entityManager;

    protected R repository;
    public BaseModelService(R repository) {
        this.repository = repository;
    }

    @Transactional
    public void create(T entity) {
        preCreate(entity);
        repository.saveAndFlush(entity);
        entityManager.refresh(entity);
        postCreate(entity);
    }


    @Transactional
    public void update(T entity) {
        preUpdate(entity);
        ID id = entity.getId();
        if (id != null && !repository.existsById(id)) {
            throw new RuntimeException("Entity not found with id: " + id);
        }
        repository.saveAndFlush(entity);
        postUpdate(entity);
    }

    @Transactional
    public void updateAll(List<T> entities) {
        entities.forEach(this::update);
    }


    protected void preCreate(T entity) {
    }

    protected void postCreate(T entity) {
    }

    protected void preUpdate(T entity) {
    }

    protected void postUpdate(T entity) {
    }

    /**
     * Fetch an entity by its ID
     */
    @Transactional(readOnly = true)
    public T findById(ID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + id));
    }

    /**
     * Delete an entity by its ID
     */
    @Transactional
    public void deleteById(ID id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Entity not found with id: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional
    public List<T> findAllById(List<ID> ids) {
        return repository.findAllById(ids);
    }

    /**
     * Retrieve all entities
     */
    @Transactional(readOnly = true)
    public List<T> findAll() {
        return repository.findAll();
    }
    /**
     * Retrieve paginated entities
     */
    @Transactional(readOnly = true)
    public Page<T> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    /**
     * Retrieve entities based on a Specification (filtering).
     * Note: This requires the repository to implement JpaSpecificationExecutor.
     */
    @Transactional(readOnly = true)
    public Page<T> findAll(Specification<T> spec, Pageable pageable) {
        if (repository != null) {
            return repository.findAll(spec, pageable);
        } else {
            throw new UnsupportedOperationException("Repository does not support Specifications");
        }
    }
}
