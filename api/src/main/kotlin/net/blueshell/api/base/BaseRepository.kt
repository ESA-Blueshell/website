package net.blueshell.api.base

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.NoRepositoryBean

/**
 * Base repository interface for common operations.
 * All entity-specific repositories can extend this interface.
 */
@NoRepositoryBean
interface BaseRepository<T> : JpaRepository<T, Long>, JpaSpecificationExecutor<T>
