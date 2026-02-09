package net.blueshell.api.user.mapper

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.util.MappingUtil
import net.blueshell.api.user.dto.AdvancedUserDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.user.model.User
import org.mapstruct.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

@Mapper(componentModel = "spring")
abstract class AdvancedUserMapper : BaseMapper<User, AdvancedUserDTO>() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Mapping(target = "initials", ignore = true)
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "prefix", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "discord")
    @Mapping(target = "dateOfBirth")
    @Mapping(target = "phoneNumber")
    @Mapping(target = "nationality")
    @Mapping(target = "photoConsent")
    @Mapping(target = "bhv")
    @Mapping(target = "ehbo")
    @Mapping(target = "newsletter")
    @Mapping(target = "gender")
    @Mapping(target = "studentNumber")
    @Mapping(target = "addressId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun fromDTO(dto: AdvancedUserDTO, @MappingTarget user: User): User

    @Mapping(target = "id")
    @Mapping(target = "initials")
    @Mapping(target = "firstName")
    @Mapping(target = "prefix")
    @Mapping(target = "lastName")
    @Mapping(target = "username")
    @Mapping(target = "discord")
    @Mapping(target = "email")
    @Mapping(target = "dateOfBirth")
    @Mapping(target = "phoneNumber")
    @Mapping(target = "nationality")
    @Mapping(target = "photoConsent")
    @Mapping(target = "bhv")
    @Mapping(target = "ehbo")
    @Mapping(target = "enabled")
    @Mapping(target = "createdAt")
    @Mapping(target = "gender")
    @Mapping(target = "studentNumber")
    @Mapping(target = "addressId")
    @Mapping(target = "newsletter")
    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "roles", expression = "java(user.getInheritedRoles())")
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(user: User): AdvancedUserDTO

    @AfterMapping
    protected fun onCreation(dto: AdvancedUserDTO, @MappingTarget user: User) {
        if (user.id != null) return

        if (hasAuthority(Role.BOARD)) {
            user.password = passwordEncoder.encode(MappingUtil.generateRandomString())
        } else {
            user.password = passwordEncoder.encode(dto.password)
        }

        applyRestrictedFields(dto, user)
    }

    @AfterMapping
    protected fun onUpdate(dto: AdvancedUserDTO, @MappingTarget user: User) {
        if (user.id == null) return

        if (hasAuthority(Role.BOARD)) {
            applyRestrictedFields(dto, user)
        }
    }

    private fun applyRestrictedFields(dto: AdvancedUserDTO, @MappingTarget user: User) {
        dto.initials?.let { user.initials = it }
        dto.firstName?.let { user.firstName = it }
        dto.prefix?.let { user.prefix = it }
        dto.lastName?.let { user.lastName = it }
        dto.email?.let { user.email = it }
        dto.username?.let { user.username = it }
    }
}
