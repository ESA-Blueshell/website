package net.blueshell.api.user.web.mapper

import net.blueshell.api.user.web.dto.SimpleUserDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.user.persistence.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class SimpleUserMapper(
    private val passwordEncoder: PasswordEncoder
) : BaseMapper<User, SimpleUserDTO>() {
    override fun fromDTO(dto: SimpleUserDTO): User = fromDTO(dto, User())

    fun fromDTO(dto: SimpleUserDTO, user: User): User {
        if (user.id == null) {
            dto.initials?.let { user.initials = it }
            dto.firstName?.let { user.firstName = it }
            dto.prefix?.let { user.prefix = it }
            dto.lastName?.let { user.lastName = it }
            dto.username?.let { user.setUsername(it) }
            dto.email?.let { user.email = it }
        }

        dto.discord?.let { user.discord = it }
        dto.phoneNumber?.let { user.phoneNumber = it }
        user.newsletter = dto.newsletter
        dto.version?.let { user.version = it }

        if (user.id == null) {
            user.setPassword(passwordEncoder.encode(dto.password))
        }

        return user
    }

    override fun toDTO(user: User): SimpleUserDTO {
        return SimpleUserDTO().also { dto ->
            dto.id = user.id
            dto.initials = user.initials
            dto.firstName = user.firstName
            dto.prefix = user.prefix
            dto.lastName = user.lastName
            dto.username = user.username
            dto.discord = user.discord
            dto.email = user.email
            dto.phoneNumber = user.phoneNumber
            dto.newsletter = user.newsletter
            dto.fullName = user.fullName
            dto.version = user.version
            dto.addressId = user.addressId
        }
    }
}

fun User.asDTO(mapper: SimpleUserMapper): SimpleUserDTO = mapper.toDTO(this)

fun SimpleUserDTO.asEntity(mapper: SimpleUserMapper): User = mapper.fromDTO(this)
