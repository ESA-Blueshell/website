package net.blueshell.api.mapper

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import net.blueshell.api.base.BaseMapper
import net.blueshell.api.common.enums.MemberType
import net.blueshell.api.common.enums.Role
import net.blueshell.api.common.util.MappingUtil
import net.blueshell.api.dto.MembershipDTO
import net.blueshell.api.model.Membership
import net.blueshell.api.validation.group.Administration
import org.mapstruct.*
import java.time.LocalDate
import java.util.function.BiConsumer


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
        if (hasAuthority(Role.BOARD)) {
            MappingUtil.applyIfFieldIsNotNull<Membership, @PastOrPresent(groups = [Administration::class]) LocalDate>(
                membership,
                dto.startDate,
                BiConsumer { obj: Membership, startDate: LocalDate -> obj!!.startDate = startDate })
            membership.endDate = dto.endDate // Must be applied, in order to be able to resume memberships
            MappingUtil.applyIfFieldIsNotNull<Membership, @NotNull(groups = [Administration::class]) MemberType>(
                membership,
                dto.memberType,
                BiConsumer { obj: Membership, memberType: MemberType -> obj!!.memberType = memberType })
            MappingUtil.applyIfFieldIsNotNull(
                membership,
                dto.incasso,
                BiConsumer { obj: Membership, incasso: Boolean -> obj!!.incasso = incasso!! })
        }
    }
}
