package net.blueshell.api.mapper;


import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.MembershipDTO;
import net.blueshell.api.model.Membership;
import org.mapstruct.*;

import static net.blueshell.api.common.util.MappingUtil.applyIfFieldIsNotNull;

@Mapper(componentModel = "spring")
public abstract class MembershipMapper extends BaseMapper<Membership, MembershipDTO> {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "memberType")
    @Mapping(target = "startDate")
    @Mapping(target = "endDate")
    @Mapping(target = "incasso")
    @Mapping(target = "version")
    public abstract MembershipDTO toDTO(Membership membership);

    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    public abstract Membership fromDTO(MembershipDTO dto, @MappingTarget Membership membership);

    @AfterMapping
    protected void afterFromDTO(MembershipDTO dto, @MappingTarget Membership membership) {
        if (hasAuthority(Role.BOARD)) {
            applyIfFieldIsNotNull(membership, dto.getStartDate(), Membership::setStartDate);
            membership.setEndDate(dto.getEndDate()); // Must be applied, in order to be able to resume memberships
            applyIfFieldIsNotNull(membership, dto.getMemberType(), Membership::setMemberType);
            applyIfFieldIsNotNull(membership, dto.isIncasso(), Membership::setIncasso);
        }
    }
}