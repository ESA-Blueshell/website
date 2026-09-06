package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.DeletedUser
import net.blueshell.api.user.persistence.SoftDeleteSentinels
import net.blueshell.api.user.persistence.AddressLifecycleRepo
import net.blueshell.api.user.persistence.AddressRepository
import net.blueshell.api.user.persistence.DeletedUserRepository
import net.blueshell.api.user.persistence.ProfileLifecycleRepo
import net.blueshell.api.user.persistence.UserRepository
import net.blueshell.api.user.persistence.AddressLifecycleSpecs
import net.blueshell.api.user.persistence.ProfileLifecycleSpecs
import net.blueshell.api.shared.event.TrackedEventPublisher
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import net.blueshell.api.user.domain.AddressLifecycleQuery
import net.blueshell.api.user.domain.ErasureException
import net.blueshell.api.user.domain.ProfileLifecycleQuery
import net.blueshell.api.user.domain.UserRestored

@Service
class UserErasureService(
    private val users: UserService,
    private val userRepository: UserRepository,
    private val deletedUsers: DeletedUserRepository,
    private val profileLifecycles: ProfileLifecycleRepo,
    private val addressLifecycles: AddressLifecycleRepo,
    private val addresses: AddressRepository,
    private val trackedEvents: TrackedEventPublisher,
    @param:Value("\${app.user-erasure.restore-window-days:90}")
    private val restoreWindowDays: Long
) {
    @Transactional(readOnly = true)
    fun findDeletedUsers(pageable: Pageable): Page<DeletedUser> {
        return deletedUsers.findAllByOrderByRestoreUntilAtAsc(pageable)
    }

    /**
     * Whether a deletion snapshot is held for [userId]. Deletion anonymises the account and keeps
     * the row for the restore window, so a deleted user still resolves by id — the snapshot is
     * what distinguishes them.
     */
    @Transactional(readOnly = true)
    fun isDeleted(userId: Long): Boolean = deletedUsers.existsById(userId)

    /**
     * Which of [userIds] hold one. One read, so a caller judging a whole selection asks once
     * rather than once per member.
     */
    @Transactional(readOnly = true)
    fun deletedIdsAmong(userIds: Collection<Long>): Set<Long> =
        if (userIds.isEmpty()) emptySet() else deletedUsers.findAllById(userIds).map { it.userId }.toSet()

    @Transactional
    fun deleteUser(userId: Long) {
        if (deletedUsers.existsById(userId)) {
            return
        }

        val user = users.findById(userId)
        val now = Instant.now()
        val restoreUntilAt = now.plus(restoreWindowDays, ChronoUnit.DAYS)
        val snapshot = DeletedUser.fromUser(user, now, restoreUntilAt)

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

        user.replaceMemberProfile(null)
        user.replaceAddress(null)

        userRepository.saveAndFlush(user)
        deletedUsers.save(snapshot)

        trackedEvents.publish { actor ->
            UserDeleted(userId = user.id!!, actor = actor)
        }
    }

    @Transactional
    fun restoreDeletedUser(userId: Long) {
        val snapshot = deletedUsers.findById(userId)
            .orElseThrow { ErasureException.NotFound(userId) }

        val now = Instant.now()
        if (snapshot.restoreUntilAt.isBefore(now)) {
            throw ErasureException.Expired(userId)
        }

        ensureNoRestoreConflicts(snapshot)
        val user = users.findById(snapshot.userId)

        // Restore soft-deleted member profile if one exists
        profileLifecycles.findOne(
            ProfileLifecycleSpecs.fromQuery(
                ProfileLifecycleQuery(userId = snapshot.userId, softDeleted = true)
            )
        ).ifPresent { profile ->
            profile.deletedAt = SoftDeleteSentinels.ACTIVE_ROW_DELETED_AT
            profile.updatedAt = now
            profileLifecycles.saveAndFlush(profile)
        }

        // Restore soft-deleted address if one exists
        val restoreAddressId = snapshot.addressId
        if (restoreAddressId != null) {
            addressLifecycles.findOne(
                AddressLifecycleSpecs.fromQuery(
                    AddressLifecycleQuery(id = restoreAddressId, softDeleted = true)
                )
            ).ifPresent { addr ->
                addr.deletedAt = SoftDeleteSentinels.ACTIVE_ROW_DELETED_AT
                addr.updatedAt = now
                addressLifecycles.saveAndFlush(addr)
            }

            addresses.findById(restoreAddressId).ifPresent { user.replaceAddress(it) }
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
        user.photoConsent = snapshot.photoConsent
        user.enabled = snapshot.enabled

        userRepository.saveAndFlush(user)
        deletedUsers.deleteById(snapshot.userId)

        trackedEvents.publish { actor ->
            UserRestored(userId = snapshot.userId, actor = actor)
        }
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

        val profileUserIds = expired.map { it.userId }.toSet()
        val memberProfilesToDelete = profileLifecycles.findAll(
            ProfileLifecycleSpecs.fromQuery(
                ProfileLifecycleQuery(userIds = profileUserIds, softDeleted = true)
            )
        )
        if (memberProfilesToDelete.isNotEmpty()) {
            profileLifecycles.deleteAllInBatch(memberProfilesToDelete)
        }

        val addressIds = expired.mapNotNull { it.addressId }.toSet()
        if (addressIds.isNotEmpty()) {
            val addressesToDelete = addressLifecycles.findAll(
                AddressLifecycleSpecs.fromQuery(
                    AddressLifecycleQuery(ids = addressIds, softDeleted = true)
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
            throw ErasureException.Conflict("username is already in use.")
        }
        if (userRepository.existsByEmail(snapshot.email)) {
            throw ErasureException.Conflict("email is already in use.")
        }
        val discord = snapshot.discord?.takeIf { it.isNotBlank() }
        if (discord != null && userRepository.existsByDiscord(discord)) {
            throw ErasureException.Conflict("discord is already in use.")
        }
        val phone = snapshot.phoneNumber?.takeIf { it.isNotBlank() }
        if (phone != null && userRepository.existsByPhoneNumber(phone)) {
            throw ErasureException.Conflict("phone number is already in use.")
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
