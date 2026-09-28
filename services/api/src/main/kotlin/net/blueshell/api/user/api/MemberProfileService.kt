package net.blueshell.api.user.api

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.user.persistence.MemberProfile
import net.blueshell.api.user.persistence.MemberProfileRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class MemberProfileService(
    private val repository: MemberProfileRepository,
) {
    // Read back after each write, so the columns the database fills are on the answer.
    @PersistenceContext
    private lateinit var em: EntityManager

    private fun written(row: MemberProfile): MemberProfile = repository.saveAndFlush(row).also(em::refresh)

    @Transactional(readOnly = true)
    fun findById(id: Long): MemberProfile =
        repository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "MemberProfile not found with id: $id")
        }

    @Transactional
    fun update(profile: MemberProfile): MemberProfile = written(profile)
}
