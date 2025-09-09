package net.blueshell.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.common.enums.MemberType;
import net.blueshell.api.validation.group.Administration;
import net.blueshell.api.validation.membership.NoExistingMembershipForUserId;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
public class MembershipDTO extends BaseDTO {

    private Long id;

    @NotNull
    @NoExistingMembershipForUserId
    private Long userId;

    @NotNull
    private MemberType memberType;

    @NotNull
    private Date date;

    @NotNull
    private String city;

    @NotNull
    private String country;

    @NotNull
    private Date startDate;

    private Date endDate;

    @JsonProperty
    @NotNull
    private boolean incasso;
}
