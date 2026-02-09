package net.blueshell.api.user.api.mapper

import net.blueshell.api.user.api.dto.SimpleUserDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.user.domain.model.User
import org.mapstruct.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

@Mapper(componentModel = "spring")
abstract class SimpleUserMapper : BaseMapper<User, SimpleUserDTO>() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Mapping(target = "initials", conditionExpression = "java(user.getId() == null)")
    @Mapping(target = "firstName", conditionExpression = "java(user.getId() == null)")
    @Mapping(target = "prefix", conditionExpression = "java(user.getId() == null)")
    @Mapping(target = "lastName", conditionExpression = "java(user.getId() == null)")
    @Mapping(target = "username", conditionExpression = "java(user.getId() == null)")
    @Mapping(target = "email", conditionExpression = "java(user.getId() == null)")
    @Mapping(target = "discord")
    @Mapping(target = "phoneNumber")
    @Mapping(target = "newsletter")
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    abstract fun fromDTO(dto: SimpleUserDTO, @MappingTarget user: User): User

    @Mapping(target = "id")
    @Mapping(target = "initials")
    @Mapping(target = "firstName")
    @Mapping(target = "prefix")
    @Mapping(target = "lastName")
    @Mapping(target = "username")
    @Mapping(target = "discord")
    @Mapping(target = "email")
    @Mapping(target = "phoneNumber")
    @Mapping(target = "newsletter")
    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "version")
    @Mapping(target = "addressId")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(user: User): SimpleUserDTO

    @AfterMapping
    protected fun afterFromDTO(dto: SimpleUserDTO, @MappingTarget user: User) {
        if (user.id != null) return

        user.password = passwordEncoder.encode(dto.password)
    }
}
