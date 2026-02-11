package net.blueshell.api.domain.user.web.mapping

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.shared.util.MappingUtil
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.web.dto.AdvancedUserDTO
import net.blueshell.api.domain.user.web.dto.SimpleUserDTO
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import tech.mappie.api.ObjectMappie

object UserToAdvancedUserDTOMapper : ObjectMappie<User, AdvancedUserDTO>() {
    override fun map(from: User) = mapping {
        AdvancedUserDTO::password fromValue "" // Never expose the password in the DTO
        AdvancedUserDTO::roles fromProperty from::inheritedRoles
    }
}

object UserToSimpleUserDTOMapper : ObjectMappie<User, SimpleUserDTO>() {
    override fun map(from: User) = mapping {
        SimpleUserDTO::password fromValue "" // Never expose the password in the DTO
    }
}

fun AdvancedUserDTO.asEntity(
    user: User = User(),
    passwordEncoder: PasswordEncoder
): User {
    discord?.let { user.discord = it }
    dateOfBirth?.let { user.dateOfBirth = it }
    phoneNumber?.let { user.phoneNumber = it }
    nationality?.let { user.nationality = it }
    user.photoConsent = photoConsent!!
    user.bhv = bhv!!
    user.ehbo = ehbo!!
    user.newsletter = newsletter!!
    gender?.let { user.gender = it }
    studentNumber?.let { user.studentNumber = it }
    addressId?.let { user.address = Address::class.asRef(it) }
    version?.let { user.version = it }

    val canEditIdentityFields = user.id == null || hasAuthority(Role.BOARD)

    if (user.id == null) {
        if (hasAuthority(Role.BOARD)) {
            user.password = passwordEncoder.encode(MappingUtil.generateRandomString())
        } else {
            user.password = passwordEncoder.encode(password)
        }
    }
    if (canEditIdentityFields) {
        applyUserIdentityFields(this, user)
    }

    return user
}

fun SimpleUserDTO.asEntity(
    user: User = User(),
    passwordEncoder: PasswordEncoder
): User {
    if (user.id == null) {
        applyUserIdentityFields(this, user)
    }

    discord?.let { user.discord = it }
    phoneNumber?.let { user.phoneNumber = it }
    user.newsletter = newsletter!!
    version?.let { user.version = it }

    if (user.id == null) {
        user.password = passwordEncoder.encode(password)
    }

    return user
}

private fun applyUserIdentityFields(dto: AdvancedUserDTO, user: User) {
    dto.username?.let { user.username = it }
    dto.email?.let { user.email = it }
    dto.initials?.let { user.initials = it }
    dto.firstName?.let { user.firstName = it }
    dto.prefix?.let { user.prefix = it }
    dto.lastName?.let { user.lastName = it }
}

private fun applyUserIdentityFields(dto: SimpleUserDTO, user: User) {
    dto.username?.let { user.username = it }
    dto.email?.let { user.email = it }
    dto.initials?.let { user.initials = it }
    dto.firstName?.let { user.firstName = it }
    dto.prefix?.let { user.prefix = it }
    dto.lastName?.let { user.lastName = it }
}

private fun hasAuthority(role: Role): Boolean {
    val authentication = SecurityContextHolder.getContext().authentication
    return authentication != null && authentication.authorities.any { a: GrantedAuthority? ->
        a?.authority == role.toString()
    }
}

fun User.asAdvancedDto(): AdvancedUserDTO = UserToAdvancedUserDTOMapper.map(this)

fun User.asSimpleDto(): SimpleUserDTO = UserToSimpleUserDTOMapper.map(this)
