package net.blueshell.api.mapper;


import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.common.enums.MemberType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.MembershipDTO;
import net.blueshell.api.model.Membership;
import org.mapstruct.*;

import java.time.Instant;
import java.util.Date;

import static net.blueshell.api.common.util.MappingUtil.applyIfFieldIsNotNull;

@Mapper(componentModel = "spring")
public abstract class MembershipMapper extends BaseMapper<Membership, MembershipDTO> {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "memberType")
    @Mapping(target = "city")
    @Mapping(target = "country")
    @Mapping(target = "startDate")
    @Mapping(target = "endDate")
    @Mapping(target = "incasso")
    public abstract MembershipDTO toDTO(Membership membership);

    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "city")
    @Mapping(target = "country")
    @BeanMapping(ignoreByDefault = true)
    public abstract Membership fromDTO(MembershipDTO dto, @MappingTarget Membership membership);

    @AfterMapping
    protected void afterFromDTO(MembershipDTO dto, @MappingTarget Membership membership) {
        if (hasAuthority(Role.BOARD)) {
            applyIfFieldIsNotNull(membership, dto.getStartDate(), Membership::setStartDate);
            applyIfFieldIsNotNull(membership, dto.getEndDate(), Membership::setEndDate);
            applyIfFieldIsNotNull(membership, dto.getMemberType(), Membership::setMemberType);
            applyIfFieldIsNotNull(membership, dto.isIncasso(), Membership::setIncasso);
        } else {
            membership.setStartDate((java.sql.Date) Date.from(Instant.now()));
            membership.setMemberType(MemberType.REGULAR);
        }
    }
}