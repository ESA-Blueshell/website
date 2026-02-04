package net.blueshell.api.mapper.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.base.BaseMapper
import net.blueshell.api.common.enums.Role
import net.blueshell.api.common.util.MappingUtil
import net.blueshell.api.dto.user.AdvancedUserDTO
import net.blueshell.api.model.User
import org.mapstruct.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.function.BiConsumer

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
        MappingUtil.applyIfFieldIsNotNull<User, @NotBlank String>(
            user,
            dto.initials,
            BiConsumer { obj: User, initials: String -> obj!!.initials = initials })
        MappingUtil.applyIfFieldIsNotNull<User, @NotBlank String>(
            user,
            dto.firstName,
            BiConsumer { obj: User, firstName: String -> obj!!.firstName = firstName })
        MappingUtil.applyIfFieldIsNotNull<User, String>(
            user,
            dto.prefix,
            BiConsumer { obj: User, prefix: String -> obj!!.prefix = prefix })
        MappingUtil.applyIfFieldIsNotNull<User, @NotBlank String>(
            user,
            dto.lastName,
            BiConsumer { obj: User, lastName: String -> obj!!.lastName = lastName })
        MappingUtil.applyIfFieldIsNotNull<User, @NotBlank @Email String>(
            user,
            dto.email,
            BiConsumer { obj: User, email: String -> obj!!.email = email })
        MappingUtil.applyIfFieldIsNotNull<User, @NotBlank String>(
            user,
            dto.username,
            BiConsumer { obj: User, username: String -> obj!!.username = username })
    }
}
