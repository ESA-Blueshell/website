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
abstract class AdvancedUserMapper : BaseMapper<User?, AdvancedUserDTO?>() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

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
    abstract override fun toDTO(user: User?): AdvancedUserDTO?

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
    abstract fun fromDTO(dto: AdvancedUserDTO?, @MappingTarget user: User?): User?

    @AfterMapping
    protected fun onCreation(dto: AdvancedUserDTO, @MappingTarget user: User) {
        if (user.getId() != null) return

        if (hasAuthority(Role.BOARD)) {
            user.setPassword(passwordEncoder.encode(MappingUtil.generateRandomString()))
        } else {
            user.setPassword(passwordEncoder.encode(dto.getPassword()))
        }

        applyRestrictedFields(dto, user)
    }

    @AfterMapping
    protected fun onUpdate(dto: AdvancedUserDTO, @MappingTarget user: User) {
        if (user.getId() == null) return

        if (hasAuthority(Role.BOARD)) {
            applyRestrictedFields(dto, user)
        }
    }

    private fun applyRestrictedFields(dto: AdvancedUserDTO, @MappingTarget user: User?) {
        MappingUtil.applyIfFieldIsNotNull<User?, @NotBlank String?>(
            user,
            dto.getInitials(),
            BiConsumer { obj: User?, initials: String? -> obj!!.setInitials(initials) })
        MappingUtil.applyIfFieldIsNotNull<User?, @NotBlank String?>(
            user,
            dto.getFirstName(),
            BiConsumer { obj: User?, firstName: String? -> obj!!.setFirstName(firstName) })
        MappingUtil.applyIfFieldIsNotNull<User?, String?>(
            user,
            dto.getPrefix(),
            BiConsumer { obj: User?, prefix: String? -> obj!!.setPrefix(prefix) })
        MappingUtil.applyIfFieldIsNotNull<User?, @NotBlank String?>(
            user,
            dto.getLastName(),
            BiConsumer { obj: User?, lastName: String? -> obj!!.setLastName(lastName) })
        MappingUtil.applyIfFieldIsNotNull<User?, @NotBlank @Email String?>(
            user,
            dto.getEmail(),
            BiConsumer { obj: User?, email: String? -> obj!!.setEmail(email) })
        MappingUtil.applyIfFieldIsNotNull<User?, @NotBlank String?>(
            user,
            dto.getUsername(),
            BiConsumer { obj: User?, username: String? -> obj!!.setUsername(username) })
    }
}
