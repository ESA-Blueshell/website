package net.blueshell.api.mapper.event;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.GuestDTO;
import net.blueshell.api.model.event.Guest;
import org.mapstruct.*;

import static net.blueshell.api.common.util.Util.getRandomCapitalString;

@Mapper(componentModel = "spring")
public abstract class GuestMapper extends BaseMapper<Guest, GuestDTO> {
    @Mapping(target = "name")
    @Mapping(target = "discord")
    @Mapping(target = "email")
    @Mapping(target = "phoneNumber")
    @Mapping(target = "accessToken", ignore = true)
    @BeanMapping(ignoreByDefault = true)
    public abstract void fromDTO(GuestDTO dto, @MappingTarget Guest guest);

    @AfterMapping
    protected void afterFromDTO(GuestDTO dto, @MappingTarget Guest guest) {
        if (guest.getAccessToken() == null) {
            guest.setAccessToken(getRandomCapitalString(30));
        }
    }

    @Mapping(target = "name")
    @Mapping(target = "discord")
    @Mapping(target = "email")
    @Mapping(target = "phoneNumber")
    @Mapping(target = "accessToken")
    @BeanMapping(ignoreByDefault = true)
    public abstract GuestDTO toDTO(Guest guest);
}
