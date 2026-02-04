package net.blueshell.api.mapper

import net.blueshell.api.common.enums.Role
import net.blueshell.api.model.User
import net.blueshell.clients.brevo.model.CreateContact
import net.blueshell.clients.brevo.model.UpdateContact
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Named

@Mapper(componentModel = "spring")
interface BrevoContactMapper {
    @Mapping(target = "email", source = "email")
    @Mapping(target = "extId", source = "id")
    @Mapping(target = "attributes", source = ".", qualifiedByName = ["toAttributes"])
    @BeanMapping(ignoreByDefault = true)
    fun toCreate(user: User): CreateContact

    @Mapping(target = "extId", source = "id")
    @Mapping(target = "attributes", source = ".", qualifiedByName = ["toAttributes"])
    @BeanMapping(ignoreByDefault = true)
    fun toUpdate(user: User): UpdateContact

    @Named("toAttributes")
    fun toAttributes(user: User): MutableMap<String, Any> {
        val attrs: MutableMap<String, Any> = HashMap<String, Any>()
        attrs["NEWSLETTER"] = user.newsletter
        attrs["IS_MEMBER"] = user.hasRole(Role.MEMBER)
        attrs["FIRSTNAME"] = user.firstName
        attrs["LASTNAME"] = user.lastName
        attrs["SURNAME"] = user.lastName
        attrs["SMS"] = user.phoneNumber!!
        attrs["WHATSAPP"] = user.phoneNumber!!
        return attrs
    }
}