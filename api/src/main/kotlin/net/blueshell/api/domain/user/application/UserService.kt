package net.blueshell.api.domain.user.application

import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.domain.user.application.event.UserUpdated
import net.blueshell.api.domain.user.application.exception.UserNotFoundException
import net.blueshell.api.domain.user.application.query.UserQuery
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.domain.user.persistence.spec.UserSpecifications
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.infrastructure.security.UserPrincipal
import net.blueshell.api.infrastructure.security.UserPrincipalMapper
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.function.Supplier

@Service
class UserService @Autowired constructor(
    repository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val events: AfterCommitEventPublisher,
    private val currentUserProvider: CurrentUserProvider
) : BaseModelService<User, Long, UserRepository>(repository) {
    @Throws(UsernameNotFoundException::class)
    fun loadUserByUsername(username: String): User {
        return try {
            findByUsername(username)
        } catch (ex: UserNotFoundException) {
            throw UsernameNotFoundException(ex.message, ex)
        }
    }

    fun loadUserPrincipalByUsername(username: String): UserPrincipal {
        return UserPrincipalMapper.fromUser(findByUsername(username))
    }

    @Transactional
    override fun create(entity: User): User {
        val saved = super.create(entity)
        events.publish(UserCreated(saved.id!!, createdByBoard = isBoardUser()))
        return saved
    }

    @Transactional
    override fun update(entity: User): User {
        val saved = super.update(entity)
        events.publish(UserUpdated(saved.id!!))
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


    @Transactional
    fun toggleRole(id: Long, role: Role): User {
        val user = findById(id)

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

    fun findByQuery(query: UserQuery, pageable: Pageable): Page<User> {
        val spec = UserSpecifications.fromQuery(query, currentUserProvider.currentUser())
        return repository.findAll(spec, pageable)
    }

    @Transactional
    fun updatePassword(userId: Long, rawPassword: String) {
        val user = findById(userId)
        user.password = passwordEncoder.encode(rawPassword)
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
        user.password = passwordEncoder.encode(rawPassword)
        update(user)
    }

    @Transactional
    fun updateContactId(userId: Long, contactId: Long) {
        val user = findById(userId)
        user.contactId = contactId
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
}
