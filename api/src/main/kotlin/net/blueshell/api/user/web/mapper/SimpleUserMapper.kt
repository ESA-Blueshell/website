package net.blueshell.api.user.web.mapper

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.user.web.dto.SimpleUserDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.user.persistence.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Konverter
interface SimpleUserKonverter {
    @Konvert(mappings = [Mapping(target = "password", ignore = true)])
    fun toDTO(user: User): SimpleUserDTO

    @Konvert(
        mappings = [
            Mapping(target = "addressId", ignore = true),
            Mapping(target = "createdAt", ignore = true),
            Mapping(target = "deletedAt", ignore = true),
            Mapping(target = "fullName", ignore = true),
            Mapping(target = "id", ignore = true),
            Mapping(target = "password", ignore = true),
            Mapping(target = "updatedAt", ignore = true),
        ]
    )
    fun fromDTO(dto: SimpleUserDTO): User
}

@Component
class SimpleUserMapper(
    private val passwordEncoder: PasswordEncoder
) : BaseMapper<User, SimpleUserDTO>() {
    private val konverter = konverter<SimpleUserKonverter>()

    override fun fromDTO(dto: SimpleUserDTO): User = fromDTO(dto, User())

    fun fromDTO(dto: SimpleUserDTO, user: User): User {
        val mapped = konverter.fromDTO(dto)

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
        dto.version?.let { user.version = it }

        if (user.id == null) {
            user.setPassword(passwordEncoder.encode(dto.password))
        }

        return user
    }

    override fun toDTO(user: User): SimpleUserDTO = konverter.toDTO(user)
}

fun User.asDTO(mapper: SimpleUserMapper): SimpleUserDTO = mapper.toDTO(this)

fun SimpleUserDTO.asEntity(mapper: SimpleUserMapper): User = mapper.fromDTO(this)
