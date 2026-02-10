package net.blueshell.api.user.web.mapping

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.util.MappingUtil
import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.web.dto.AdvancedUserDTO
import net.blueshell.api.user.web.dto.SimpleUserDTO
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import tech.mappie.api.ObjectMappie

object UserToAdvancedUserDTOMapper : ObjectMappie<User, AdvancedUserDTO>() {
    override fun map(from: User) = mapping {
        AdvancedUserDTO::password fromValue ""
        AdvancedUserDTO::roles fromProperty from::inheritedRoles
    }
}

object AdvancedUserDTOToUserMapper : ObjectMappie<AdvancedUserDTO, User>()

object UserToSimpleUserDTOMapper : ObjectMappie<User, SimpleUserDTO>() {
    override fun map(from: User) = mapping {
        SimpleUserDTO::password fromValue ""
    }
}

object SimpleUserDTOToUserMapper : ObjectMappie<SimpleUserDTO, User>()

fun AdvancedUserDTO.asEntity(
    user: User = User(),
    passwordEncoder: PasswordEncoder
): User {
    val mapped = AdvancedUserDTOToUserMapper.map(this)

    mapped.discord?.let { user.discord = it }
    mapped.dateOfBirth?.let { user.dateOfBirth = it }
    mapped.phoneNumber?.let { user.phoneNumber = it }
    mapped.nationality?.let { user.nationality = it }
    user.photoConsent = mapped.photoConsent
    user.bhv = mapped.bhv
    user.ehbo = mapped.ehbo
    user.newsletter = mapped.newsletter
    mapped.gender?.let { user.gender = it }
    mapped.studentNumber?.let { user.studentNumber = it }
    mapped.addressId?.let { user.addressId = it }
    version?.let { user.version = it }

    if (user.id == null) {
        if (hasAuthority(Role.BOARD)) {
            user.setPassword(passwordEncoder.encode(MappingUtil.generateRandomString()))
        } else {
            user.setPassword(passwordEncoder.encode(password))
        }
        applyRestrictedFields(this, user)
    } else if (hasAuthority(Role.BOARD)) {
        applyRestrictedFields(this, user)
    }

    return user
}

fun SimpleUserDTO.asEntity(
    user: User = User(),
    passwordEncoder: PasswordEncoder
): User {
    val mapped = SimpleUserDTOToUserMapper.map(this)

    if (user.id == null) {
        mapped.initials?.let { user.initials = it }
        mapped.firstName?.let { user.firstName = it }
        mapped.prefix?.let { user.prefix = it }
        mapped.lastName?.let { user.lastName = it }
        mapped.username?.let { user.setUsername(it) }
        mapped.email?.let { user.email = it }
    }

    mapped.discord?.let { user.discord = it }
    mapped.phoneNumber?.let { user.phoneNumber = it }
    user.newsletter = mapped.newsletter
    version?.let { user.version = it }

    if (user.id == null) {
        user.setPassword(passwordEncoder.encode(password))
    }

    return user
}

private fun applyRestrictedFields(dto: AdvancedUserDTO, user: User) {
    dto.initials?.let { user.initials = it }
    dto.firstName?.let { user.firstName = it }
    dto.prefix?.let { user.prefix = it }
    dto.lastName?.let { user.lastName = it }
    dto.email?.let { user.email = it }
    dto.username?.let { user.setUsername(it) }
}

private fun hasAuthority(role: Role): Boolean {
    val authentication = SecurityContextHolder.getContext().authentication
    return authentication != null && authentication.authorities.any { a: GrantedAuthority? ->
        a?.authority == role.toString()
    }
}

fun User.asAdvancedDto(): AdvancedUserDTO = UserToAdvancedUserDTOMapper.map(this)

fun User.asSimpleDto(): SimpleUserDTO = UserToSimpleUserDTOMapper.map(this)
