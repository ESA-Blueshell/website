package net.blueshell.api.feature.membership.mapper

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.feature.membership.dto.MembershipDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.feature.membership.model.Membership
import org.mapstruct.*


@Mapper(componentModel = "spring")
abstract class MembershipMapper : BaseMapper<Membership, MembershipDTO>() {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "memberType")
    @Mapping(target = "startDate")
    @Mapping(target = "endDate")
    @Mapping(target = "incasso")
    @Mapping(target = "version")
    abstract override fun toDTO(membership: Membership): MembershipDTO

    @Mapping(target = "userId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(dto: MembershipDTO, @MappingTarget membership: Membership): Membership

    @AfterMapping
    protected fun afterFromDTO(dto: MembershipDTO, @MappingTarget membership: Membership) {
        if (!hasAuthority(Role.BOARD)) return

        dto.startDate?.let { membership.startDate = it }
        membership.endDate = dto.endDate // Must be applied, in order to be able to resume memberships
        dto.memberType?.let { membership.memberType = it }
        dto.incasso.let { membership.incasso = it }
    }
}
