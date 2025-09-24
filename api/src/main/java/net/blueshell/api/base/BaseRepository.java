package net.blueshell.api.base;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Base repository interface for common operations.
 * All entity-specific repositories can extend this interface.
 */
@NoRepositoryBean
public interface BaseRepository<T> extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
}
