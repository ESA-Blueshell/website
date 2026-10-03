package net.blueshell.api.cohort.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface TargetDeletionRepository : JpaRepository<TargetDeletion, Long>
