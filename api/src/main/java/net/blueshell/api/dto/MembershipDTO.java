package net.blueshell.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.common.enums.MemberType;
import net.blueshell.api.validation.date.Today;
import net.blueshell.api.validation.group.Administration;
import net.blueshell.api.validation.group.Creation;
import net.blueshell.api.validation.membership.NoExistingMembershipForUserId;

import java.sql.Date;

@Data
@EqualsAndHashCode(callSuper = false)
public class MembershipDTO extends BaseDTO {

    private Long id;

    @NotNull
    @NoExistingMembershipForUserId
    private Long userId;

    @NotNull(groups = {Administration.class})
    private MemberType memberType;

    @NotNull(groups = {Creation.class})
    private String city;

    @NotNull(groups = {Creation.class})
    private String country;

    @PastOrPresent(groups = {Administration.class})
    @Today(groups = {Creation.class})
    private Date startDate;

    private Date endDate;

    @JsonProperty
    @NotNull
    private boolean incasso;
}
