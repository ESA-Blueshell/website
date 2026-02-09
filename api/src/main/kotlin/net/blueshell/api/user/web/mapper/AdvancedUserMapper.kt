package net.blueshell.api.user.web.mapper

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.util.MappingUtil
import net.blueshell.api.user.web.dto.AdvancedUserDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.user.persistence.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class AdvancedUserMapper(
    private val passwordEncoder: PasswordEncoder
) : BaseMapper<User, AdvancedUserDTO>() {
    override fun fromDTO(dto: AdvancedUserDTO): User = fromDTO(dto, User())

    fun fromDTO(dto: AdvancedUserDTO, user: User): User {
        dto.discord?.let { user.discord = it }
        dto.dateOfBirth?.let { user.dateOfBirth = it }
        dto.phoneNumber?.let { user.phoneNumber = it }
        dto.nationality?.let { user.nationality = it }
        dto.photoConsent?.let { user.photoConsent = it }
        dto.bhv?.let { user.bhv = it }
        dto.ehbo?.let { user.ehbo = it }
        user.newsletter = dto.newsletter
        dto.gender?.let { user.gender = it }
        dto.studentNumber?.let { user.studentNumber = it }
        dto.addressId?.let { user.addressId = it }
        dto.version?.let { user.version = it }

        if (user.id == null) {
            if (hasAuthority(Role.BOARD)) {
                user.setPassword(passwordEncoder.encode(MappingUtil.generateRandomString()))
            } else {
                user.setPassword(passwordEncoder.encode(dto.password))
            }
            applyRestrictedFields(dto, user)
        } else if (hasAuthority(Role.BOARD)) {
            applyRestrictedFields(dto, user)
        }

        return user
    }

    override fun toDTO(user: User): AdvancedUserDTO {
        return AdvancedUserDTO().also { dto ->
            dto.id = user.id
            dto.initials = user.initials
            dto.firstName = user.firstName
            dto.prefix = user.prefix
            dto.lastName = user.lastName
            dto.username = user.username
            dto.discord = user.discord
            dto.email = user.email
            dto.dateOfBirth = user.dateOfBirth
            dto.phoneNumber = user.phoneNumber
            dto.nationality = user.nationality
            dto.photoConsent = user.photoConsent
            dto.bhv = user.bhv
            dto.ehbo = user.ehbo
            dto.enabled = user.enabled
            dto.createdAt = user.createdAt
            dto.gender = user.gender
            dto.studentNumber = user.studentNumber
            dto.addressId = user.addressId
            dto.newsletter = user.newsletter
            dto.fullName = user.fullName
            dto.roles = user.inheritedRoles.toMutableSet()
            dto.version = user.version
        }
    }

    private fun applyRestrictedFields(dto: AdvancedUserDTO, user: User) {
        dto.initials?.let { user.initials = it }
        dto.firstName?.let { user.firstName = it }
        dto.prefix?.let { user.prefix = it }
        dto.lastName?.let { user.lastName = it }
        dto.email?.let { user.email = it }
        dto.username?.let { user.setUsername(it) }
    }
}

fun User.asDTO(mapper: AdvancedUserMapper): AdvancedUserDTO = mapper.toDTO(this)

fun AdvancedUserDTO.asEntity(mapper: AdvancedUserMapper): User = mapper.fromDTO(this)
