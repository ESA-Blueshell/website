package net.blueshell.api.mapper;


import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.MembershipDTO;
import net.blueshell.api.model.Membership;
import net.blueshell.api.service.UserService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.time.Instant;

import static net.blueshell.api.common.util.MappingUtil.applyIfFieldIsNotNull;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class MembershipMapper extends BaseMapper<Membership, MembershipDTO> {

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private UserService userService;

    public abstract MembershipDTO toDTO(Membership membership);

    @Mapping(target = "user", ignore = true)
    @Mapping(target = "userId")
    @Mapping(target = "city")
    @Mapping(target = "contributions", ignore = true)
    @Mapping(target = "incasso", ignore = true)
    @Mapping(target = "startDate", ignore = true)
    public abstract Membership fromDTO(MembershipDTO dto);

    @AfterMapping
    protected void afterFromDTO(MembershipDTO dto, @MappingTarget Membership membership) {
        if (hasAuthority(Role.BOARD)) {
            applyIfFieldIsNotNull(membership, dto.getStartDate(), Membership::setStartDate);
            applyIfFieldIsNotNull(membership, dto.getEndDate(), Membership::setEndDate);
            applyIfFieldIsNotNull(membership, dto.getMemberType(), Membership::setMemberType);
            applyIfFieldIsNotNull(membership, dto.isIncasso(), Membership::setIncasso);
        } else {
            membership.setStartDate((java.sql.Date) Date.from(Instant.now()));
        }
    }
}