package net.blueshell.api.domain.user.application.lifecycle

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.query.AddressLifecycleQuery
import net.blueshell.api.domain.user.application.query.MemberProfileLifecycleQuery
import net.blueshell.api.domain.user.persistence.DeletedUser
import net.blueshell.api.domain.user.persistence.lifecycle.LifecycleSoftDeleteTimestamps
import net.blueshell.api.domain.user.persistence.repository.AddressRepository
import net.blueshell.api.domain.user.persistence.repository.AddressLifecycleRepository
import net.blueshell.api.domain.user.persistence.repository.DeletedUserRepository
import net.blueshell.api.domain.user.persistence.repository.MemberProfileLifecycleRepository
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.domain.user.persistence.spec.AddressLifecycleSpecifications
import net.blueshell.api.domain.user.persistence.spec.MemberProfileLifecycleSpecifications
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
    private val memberProfileLifecycles: MemberProfileLifecycleRepository,
    private val addressLifecycles: AddressLifecycleRepository,
    private val addresses: AddressRepository,
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
        if (deletedUsers.existsById(userId)) {
            return
        }

        val user = users.findById(userId)
        val now = Instant.now()
        val restoreUntilAt = now.plus(restoreWindowDays, ChronoUnit.DAYS)
        val snapshot = DeletedUser.fromUser(user, now, restoreUntilAt)
        val contactId = user.contactId

        val anonymized = anonymizedIdentity(user.id!!, now)
        user.username = anonymized.username
        user.email = anonymized.email
        user.initials = "DU"
        user.firstName = "Deleted"
        user.prefix = null
        user.lastName = "User"
        user.phoneNumber = null
        user.discord = null
        user.newsletter = false
        user.enabled = false
        user.contactId = null

        if (snapshot.hadMemberProfile) {
            user.replaceMemberProfile(null)
        }
        if (snapshot.hadAddress) {
            user.replaceAddress(null)
        }

        userRepository.saveAndFlush(user)

        deletedUsers.save(snapshot)

        contactId?.let {
            jobs.enqueue(
                ContactJobs.DeleteContact,
                ContactJobs.DeleteContactPayload(userId = user.id!!, contactId = it)
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
        val user = users.findById(snapshot.userId)

        if (snapshot.hadMemberProfile) {
            val memberProfileLifecycle = memberProfileLifecycles.findOne(
                MemberProfileLifecycleSpecifications.fromQuery(
                    MemberProfileLifecycleQuery(
                        userId = snapshot.userId,
                        softDeleted = true
                    )
                )
            ).orElseThrow {
                ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot restore user: member profile is no longer restorable."
                )
            }

            memberProfileLifecycle.deletedAt = LifecycleSoftDeleteTimestamps.ACTIVE_ROW_DELETED_AT
            memberProfileLifecycle.updatedAt = now
            memberProfileLifecycles.saveAndFlush(memberProfileLifecycle)
        }

        val restoreAddressId = snapshot.addressId
        if (snapshot.hadAddress) {
            if (restoreAddressId == null) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot restore user: address is no longer restorable."
                )
            }
            val addressLifecycle = addressLifecycles.findOne(
                AddressLifecycleSpecifications.fromQuery(
                    AddressLifecycleQuery(
                        id = restoreAddressId,
                        softDeleted = true
                    )
                )
            ).orElseThrow {
                ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot restore user: address is no longer restorable."
                )
            }
            addressLifecycle.deletedAt = LifecycleSoftDeleteTimestamps.ACTIVE_ROW_DELETED_AT
            addressLifecycle.updatedAt = now
            addressLifecycles.saveAndFlush(addressLifecycle)
        }

        if (snapshot.hadAddress && restoreAddressId != null) {
            val restoredAddress = addresses.findById(restoreAddressId)
                .orElseThrow {
                    ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot restore user: restored address cannot be loaded."
                    )
                }
            user.replaceAddress(restoredAddress)
        } else {
            user.replaceAddress(null)
        }

        user.username = snapshot.username
        user.email = snapshot.email
        user.initials = snapshot.initials
        user.firstName = snapshot.firstName
        user.prefix = snapshot.prefix
        user.lastName = snapshot.lastName
        user.phoneNumber = snapshot.phoneNumber
        user.discord = snapshot.discord
        user.newsletter = snapshot.newsletter
        user.enabled = snapshot.enabled
        user.contactId = null

        userRepository.saveAndFlush(user)

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

        val profileUserIds = expired
            .filter { it.hadMemberProfile }
            .map { it.userId }
            .toSet()
        if (profileUserIds.isNotEmpty()) {
            val memberProfilesToDelete = memberProfileLifecycles.findAll(
                MemberProfileLifecycleSpecifications.fromQuery(
                    MemberProfileLifecycleQuery(
                        userIds = profileUserIds,
                        softDeleted = true
                    )
                )
            )
            if (memberProfilesToDelete.isNotEmpty()) {
                memberProfileLifecycles.deleteAllInBatch(memberProfilesToDelete)
            }
        }

        val addressIds = expired.mapNotNull { snapshot ->
            snapshot.addressId?.takeIf { snapshot.hadAddress }
        }.toSet()
        if (addressIds.isNotEmpty()) {
            val addressesToDelete = addressLifecycles.findAll(
                AddressLifecycleSpecifications.fromQuery(
                    AddressLifecycleQuery(
                        ids = addressIds,
                        softDeleted = true
                    )
                )
            )
            if (addressesToDelete.isNotEmpty()) {
                addressLifecycles.deleteAllInBatch(addressesToDelete)
            }
        }

        deletedUsers.deleteAllInBatch(expired)
        return expired.size
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
