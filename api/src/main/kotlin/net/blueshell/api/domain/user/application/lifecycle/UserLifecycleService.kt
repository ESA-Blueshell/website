package net.blueshell.api.domain.user.application.lifecycle

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.DeletedUser
import net.blueshell.api.domain.user.persistence.repository.DeletedUserRepository
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class UserLifecycleService(
    private val users: UserService,
    private val userRepository: UserRepository,
    private val deletedUsers: DeletedUserRepository,
    private val jobs: TrackedJobDispatcher,
    @param:Value("\${app.user-lifecycle.restore-window-days:90}")
    private val restoreWindowDays: Long
) {
    @Transactional(readOnly = true)
    fun findDeletedUsers(pageable: Pageable): Page<DeletedUser> {
        return deletedUsers.findAllByOrderByRestoreUntilAtAsc(pageable)
    }

    @Transactional
    fun deleteUser(userId: Long) {
        val existingSnapshot = deletedUsers.findById(userId).orElse(null)
        val user = findActiveUserOrAlreadyDeleted(userId, existingSnapshot != null)
        if (user == null) {
            return
        }

        val now = Instant.now()
        val restoreUntilAt = now.plus(restoreWindowDays, ChronoUnit.DAYS)
        deletedUsers.save(DeletedUser.fromUser(user, now, restoreUntilAt))

        val anonymized = anonymizedIdentity(user.id!!, now)
        val updatedRows = userRepository.markDeletedAndPseudonymized(
            id = user.id!!,
            username = anonymized.username,
            email = anonymized.email,
            initials = "DU",
            firstName = "Deleted",
            prefix = null,
            lastName = "User",
            phoneNumber = null,
            discord = null,
            newsletter = false,
            enabled = false,
            deletedAt = now,
            updatedAt = now
        )

        if (updatedRows == 0) {
            return
        }

        user.contactId?.let { contactId ->
            jobs.enqueue(
                ContactJobs.DeleteContact,
                ContactJobs.DeleteContactPayload(userId = user.id!!, contactId = contactId)
            )
        }
    }

    @Transactional
    fun restoreDeletedUser(userId: Long) {
        val snapshot = deletedUsers.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Deleted user not found for id: $userId") }

        val now = Instant.now()
        if (snapshot.restoreUntilAt.isBefore(now)) {
            throw ResponseStatusException(HttpStatus.GONE, "Restore window has expired for user id: $userId")
        }

        ensureNoRestoreConflicts(snapshot)

        val restoredRows = userRepository.restoreFromDeletedSnapshot(
            id = snapshot.userId,
            username = snapshot.username,
            email = snapshot.email,
            initials = snapshot.initials,
            firstName = snapshot.firstName,
            prefix = snapshot.prefix,
            lastName = snapshot.lastName,
            phoneNumber = snapshot.phoneNumber,
            discord = snapshot.discord,
            newsletter = snapshot.newsletter,
            enabled = snapshot.enabled,
            updatedAt = now
        )

        if (restoredRows == 0) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "User cannot be restored because it is no longer in deleted-user state."
            )
        }

        deletedUsers.deleteById(snapshot.userId)
    }

    @Transactional
    fun finalizeExpiredDeletedUsers(batchSize: Int): Int {
        if (batchSize <= 0) {
            throw IllegalArgumentException("batchSize must be positive")
        }
        val expired = deletedUsers.findByRestoreUntilAtLessThanEqualOrderByRestoreUntilAtAsc(
            Instant.now(),
            PageRequest.of(0, batchSize)
        )
        if (expired.isEmpty()) {
            return 0
        }
        deletedUsers.deleteAllInBatch(expired)
        return expired.size
    }

    private fun findActiveUserOrAlreadyDeleted(userId: Long, snapshotExists: Boolean): net.blueshell.api.domain.user.persistence.User? {
        return try {
            users.findById(userId)
        } catch (ex: ResponseStatusException) {
            if (ex.statusCode == HttpStatus.NOT_FOUND && snapshotExists) {
                null
            } else {
                throw ex
            }
        }
    }

    private fun ensureNoRestoreConflicts(snapshot: DeletedUser) {
        if (userRepository.existsByUsername(snapshot.username)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot restore user: username is already in use.")
        }
        if (userRepository.existsByEmail(snapshot.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot restore user: email is already in use.")
        }
        val discord = snapshot.discord?.takeIf { it.isNotBlank() }
        if (discord != null && userRepository.existsByDiscord(discord)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot restore user: discord is already in use.")
        }
        val phone = snapshot.phoneNumber?.takeIf { it.isNotBlank() }
        if (phone != null && userRepository.existsByPhoneNumber(phone)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot restore user: phone number is already in use.")
        }
    }

    private fun anonymizedIdentity(userId: Long, now: Instant): AnonymizedIdentity {
        val suffix = "$userId-${now.toEpochMilli()}"
        return AnonymizedIdentity(
            username = "deleted-$suffix",
            email = "deleted-$suffix@deleted.invalid"
        )
    }

    private data class AnonymizedIdentity(
        val username: String,
        val email: String
    )
}
