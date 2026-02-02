package net.blueshell.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.common.enums.MemberType;
import net.blueshell.api.validation.date.Today;
import net.blueshell.api.validation.group.Administration;
import net.blueshell.api.validation.group.Creation;
import net.blueshell.api.validation.membership.NoExistingMembershipForUserId;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Membership")
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

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @PastOrPresent(groups = {Administration.class})
    @Today(groups = {Creation.class})
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @JsonProperty
    @NotNull
    private boolean incasso;
}
