package net.blueshell.api.user.web.mapper

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.util.MappingUtil
import net.blueshell.api.user.web.dto.AdvancedUserDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.user.persistence.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Konverter
interface AdvancedUserKonverter {
    @Konvert(
        mappings = [
            Mapping(target = "password", ignore = true),
            Mapping(target = "roles", source = "inheritedRoles"),
        ]
    )
    fun toDTO(user: User): AdvancedUserDTO

    @Konvert(
        mappings = [
            Mapping(target = "createdAt", ignore = true),
            Mapping(target = "deletedAt", ignore = true),
            Mapping(target = "enabled", ignore = true),
            Mapping(target = "fullName", ignore = true),
            Mapping(target = "id", ignore = true),
            Mapping(target = "password", ignore = true),
            Mapping(target = "roles", ignore = true),
            Mapping(target = "updatedAt", ignore = true),
        ]
    )
    fun fromDTO(dto: AdvancedUserDTO): User
}

@Component
class AdvancedUserMapper(
    private val passwordEncoder: PasswordEncoder
) : BaseMapper<User, AdvancedUserDTO>() {
    private val konverter = konverter<AdvancedUserKonverter>()

    override fun fromDTO(dto: AdvancedUserDTO): User = fromDTO(dto, User())

    fun fromDTO(dto: AdvancedUserDTO, user: User): User {
        val mapped = konverter.fromDTO(dto)

        mapped.discord?.let { user.discord = it }
        mapped.dateOfBirth?.let { user.dateOfBirth = it }
        mapped.phoneNumber?.let { user.phoneNumber = it }
        mapped.nationality?.let { user.nationality = it }
        mapped.photoConsent.let { user.photoConsent = it }
        mapped.bhv.let { user.bhv = it }
        mapped.ehbo.let { user.ehbo = it }
        user.newsletter = mapped.newsletter
        mapped.gender?.let { user.gender = it }
        mapped.studentNumber?.let { user.studentNumber = it }
        mapped.addressId?.let { user.addressId = it }
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

    override fun toDTO(user: User): AdvancedUserDTO = konverter.toDTO(user)

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
