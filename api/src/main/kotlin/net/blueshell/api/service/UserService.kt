package net.blueshell.api.service

import net.blueshell.api.base.BaseModelService
import net.blueshell.api.common.enums.Role
import net.blueshell.api.controller.filter.UserFilter
import net.blueshell.api.model.User
import net.blueshell.api.repository.UserRepository
import net.blueshell.api.repository.spec.UserSpecifications
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.function.Supplier

@Service
class UserService @Autowired constructor(repository: UserRepository, private val passwordEncoder: PasswordEncoder) :
    BaseModelService<User, Long, UserRepository>(repository), UserDetailsService {
    @Throws(UsernameNotFoundException::class)
    override fun loadUserByUsername(username: String): User {
        return findByUsername(username)
    }

    fun findByUsername(username: String): User {
        return repository!!.findByUsername(username).orElseThrow<ResponseStatusException>(Supplier {
            ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found with username: $username"
            )
        })
    }

    fun existsByUsername(username: String): Boolean {
        return repository!!.existsByUsername(username)
    }

    fun existsByEmail(email: String): Boolean {
        return repository!!.existsByEmail(email)
    }

    fun existsByPhoneNumber(phoneNumber: String): Boolean {
        return repository!!.existsByPhoneNumber(phoneNumber)
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

    fun findByFilter(filter: UserFilter, pageable: Pageable): Page<User> {
        val spec = UserSpecifications.fromFilter(filter, principal)
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
}
