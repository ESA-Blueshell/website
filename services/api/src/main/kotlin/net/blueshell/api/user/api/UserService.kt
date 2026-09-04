package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.persistence.UserRepository
import net.blueshell.api.user.persistence.UserSpecifications
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.shared.security.UserPrincipalMapper
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.function.Supplier
import net.blueshell.api.user.domain.UserQuery

@Service
class UserService @Autowired constructor(
    repository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val trackedEvents: TrackedEventPublisher,
    private val currentUserProvider: CurrentUserProvider
) : BaseModelService<User, Long, UserRepository>(repository) {
    @Throws(UsernameNotFoundException::class)
    fun loadUserByUsername(username: String): User {
        return try {
            findByUsername(username)
        } catch (ex: UserNotFoundException) {
            throw UsernameNotFoundException(ex.message ?: "User not found", ex)
        }
    }

    // Hot path: JwtAuthFilter calls this on every authenticated request.
    // Cached in Valkey (short TTL), evicted whenever the user is mutated.
    @Cacheable("users.principalByUsername")
    fun loadUserPrincipalByUsername(username: String): UserPrincipal {
        return UserPrincipalMapper.fromUser(findByUsername(username))
    }

    @Transactional
    override fun create(entity: User): User {
        val saved = super.create(entity)
        trackedEvents.publish { actor ->
            UserCreated(
                saved.id!!,
                createdByBoard = isBoardUser(),
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    @CacheEvict("users.principalByUsername", key = "#entity.username")
    override fun update(entity: User): User {
        val saved = super.update(entity)
        trackedEvents.publish { actor ->
            UserUpdated(
                saved.id!!,
                actor = actor
            )
        }
        return saved
    }

    fun findByUsername(username: String): User {
        return repository.findByUsername(username).orElseThrow(Supplier {
            UserNotFoundException("User not found with username: $username")
        })
    }

    fun existsByUsername(username: String): Boolean {
        return repository.existsByUsername(username)
    }

    fun existsByUsernameAndIdNot(username: String, id: Long): Boolean {
        return repository.existsByUsernameAndIdNot(username, id)
    }

    fun existsByEmail(email: String): Boolean {
        return repository.existsByEmail(email)
    }

    fun existsByEmailAndIdNot(email: String, id: Long): Boolean {
        return repository.existsByEmailAndIdNot(email, id)
    }

    fun existsByPhoneNumber(phoneNumber: String): Boolean {
        return repository.existsByPhoneNumber(phoneNumber)
    }

    fun existsByPhoneNumberAndIdNot(phoneNumber: String, id: Long): Boolean {
        return repository.existsByPhoneNumberAndIdNot(phoneNumber, id)
    }

    fun existsByDiscord(discord: String): Boolean {
        return repository.existsByDiscord(discord)
    }

    fun existsByDiscordAndIdNot(discord: String, id: Long): Boolean {
        return repository.existsByDiscordAndIdNot(discord, id)
    }

    fun existsActiveMembershipByUserId(userId: Long): Boolean =
        repository.existsActiveMembershipByUserId(userId)

    /**
     * Batch lookup. Returns the users whose ids are in [ids] in unspecified
     * order; ids without a corresponding active user are silently skipped.
     * Used by cross-domain admin views (e.g. cohort dashboard) that resolve
     * a list of user ids to display rows in one round trip.
     */
    /** Ids of accounts that have not been activated. */
    /** Everybody who opted into the newsletter, whether or not they activated an account. */
    fun findNewsletterSubscriberIds(): Set<Long> = repository.findIdsByNewsletterTrue().toSet()

    fun findAllDisabledIds(): List<Long> = repository.findIdsByEnabledFalse()

    fun findAllByIds(ids: Collection<Long>): List<User> =
        if (ids.isEmpty()) emptyList() else repository.findAllById(ids).toList()

    /**
     * Whether the user was soft-deleted, as against never having been here. The cohort engine
     * reads it to keep a deleted member's ledger rows rather than pushing a REMOVE for each.
     */
    fun isSoftDeleted(userId: Long): Boolean =
        repository.findSoftDeletedUserId(userId) != null

    fun findSoftDeletedIds(userIds: Collection<Long>): Set<Long> =
        if (userIds.isEmpty()) emptySet() else repository.findSoftDeletedUserIds(userIds).toSet()

    /**
     * Up to [limit] active user ids greater than [afterId], ascending — keyset
     * pagination for bulk fan-out (e.g. the cohort all-users reconcile) so a
     * sweep never loads the whole user table or holds one transaction across it.
     */
    fun findActiveIdsAfter(afterId: Long, limit: Int): List<Long> =
        repository.findActiveIdsAfter(afterId, PageRequest.of(0, limit))

    @Transactional
    fun toggleRole(id: Long, role: Role): User {
        val user = findById(id)
        val current = currentUserProvider.currentUser()

        if (current?.id == id && !user.hasRole(role)) {
            throw AccessDeniedException("Cannot elevate own privileges")
        }

        if (user.hasRole(role)) {
            user.removeRole(role)
        } else {
            user.addRole(role)
        }
        update(user)
        return user
    }

    @Transactional
    fun addRole(id: Long, role: Role) {
        val user = findById(id)
        if (!user.hasRole(role)) {
            user.addRole(role)
            update(user)
        }
    }

    @Transactional
    fun removeRole(id: Long, role: Role) {
        val user = findById(id)
        if (user.hasRole(role)) {
            user.removeRole(role)
            update(user)
        }
    }

    /**
     * Users matching a query, always in a defined order: an unsorted read returns whatever the
     * plan produces, which looks stable locally and changes when the predicates or indexes do.
     * A caller that asks for an order gets it, and one that does not gets creation order.
     */
    fun findByQuery(query: UserQuery, pageable: Pageable): Page<User> {
        val spec = UserSpecifications.fromQuery(query, currentUserProvider.currentUser())
        return repository.findAll(spec, withDefaultOrder(pageable))
    }

    private fun withDefaultOrder(pageable: Pageable): Pageable = when {
        pageable.sort.isSorted -> pageable
        pageable.isPaged -> PageRequest.of(pageable.pageNumber, pageable.pageSize, DEFAULT_USER_ORDER)
        else -> Pageable.unpaged(DEFAULT_USER_ORDER)
    }

    @Transactional
    fun updatePassword(userId: Long, rawPassword: String) {
        val user = findById(userId)
        user.password = requireNotNull(passwordEncoder.encode(rawPassword)) { "PasswordEncoder returned null hash" }
        update(user)
    }

    @Transactional
    fun activateUser(userId: Long) {
        val user = findById(userId)
        user.enabled = true
        update(user)
    }

    @Transactional
    fun setUsernameAndPassword(userId: Long, username: String, rawPassword: String) {
        val user = findById(userId)
        user.username = username
        user.password = requireNotNull(passwordEncoder.encode(rawPassword)) { "PasswordEncoder returned null hash" }
        update(user)
    }

    private fun isBoardUser(): Boolean {
        val current = currentUserProvider.currentUser() ?: return false
        return hasAuthority(current, Role.BOARD)
    }

    private fun hasAuthority(user: CurrentUser, role: Role): Boolean {
        val inherited = user.roles.flatMap { it.allInheritedRoles }
        return inherited.any { it.matchesRole(role) }
    }

    private companion object {
        /** Creation order: the newest accounts land at the end, where they can be found. */
        val DEFAULT_USER_ORDER: Sort = Sort.by(Sort.Direction.ASC, "id")
    }
}
