package net.blueshell.api.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.common.enums.MemberType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.dto.MembershipDTO;
import net.blueshell.api.validation.group.Administration;
import net.blueshell.api.validation.group.Creation;
import net.blueshell.api.validation.group.Member;
import net.blueshell.api.validation.group.Update;
import net.blueshell.api.validation.user.UniqueUser;
import net.blueshell.api.validation.user.ValidMobilePhoneNumber;

import java.sql.Timestamp;
import java.util.Set;

@Data
@UniqueUser
@EqualsAndHashCode(callSuper = false)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimpleUserDTO.class, name = "simple"),
        @JsonSubTypes.Type(value = AdvancedUserDTO.class, name = "advanced"),
})
public class AdvancedUserDTO extends SimpleUserDTO {

    @NotNull(groups = {Update.class})
    private Long id;

    @JsonProperty
    @NotBlank(groups = {Creation.class, Administration.class})
    private String initials;

    @JsonProperty
    @NotBlank(groups = {Creation.class, Administration.class})
    private String firstName;

    @JsonProperty
    private String prefix;

    @JsonProperty
    @NotBlank(groups = {Creation.class, Administration.class})
    private String lastName;

    @JsonProperty
    private String fullName;

    @JsonProperty
    @NotBlank(groups = {Creation.class})
    private String username;

    @JsonProperty
    private Set<Role> roles;

    @JsonProperty
    @NotBlank(groups = {Creation.class, Update.class})
    private String discord;

    @JsonProperty
    @NotNull(groups = {Member.class})
    private Timestamp dateOfBirth;

    @JsonProperty
    @NotBlank(groups = {Creation.class, Administration.class})
    @Email(groups = {Creation.class, Administration.class})
    private String email;

    @JsonProperty
    @NotBlank(groups = {Creation.class, Update.class})
    @ValidMobilePhoneNumber(groups = {Creation.class, Update.class})
    private String phoneNumber;

    @JsonProperty
    @NotBlank(groups = {Member.class})
    private String postalCode;

    @JsonProperty
    @NotBlank(groups = {Member.class})
    private String address;

    @JsonProperty
    @NotBlank(groups = {Member.class, Update.class})
    private String city;

    @JsonProperty
    @NotBlank(groups = {Member.class})
    private String country;

    @JsonProperty
    @NotBlank(groups = {Member.class})
    private String nationality;

    @JsonProperty
    @Valid
    @NotNull(groups = {Member.class})
    private FileDTO signature;

    @Valid
    private MembershipDTO membership;

    @JsonProperty
    @NotNull(groups = {Member.class, Update.class})
    private boolean newsletter;

    @JsonProperty
    @NotNull(groups = {Member.class, Update.class})
    private boolean photoConsent;

    @JsonProperty
    @NotNull(groups = {Member.class, Update.class})
    private boolean ehbo;

    @JsonProperty
    @NotNull(groups = {Member.class, Update.class})
    private boolean bhv;

    @JsonProperty
    private boolean enabled;

    @JsonProperty
    @NotNull(groups = {Administration.class})
    private boolean incasso;

    @JsonProperty
    private Timestamp createdAt;

    @JsonProperty
    private String gender;

    @JsonProperty
    private String study;

    @JsonProperty
    private String studentNumber;

    @JsonProperty
    @JsonIgnore
    @NotBlank(groups = Creation.class)
    private String password;
}
