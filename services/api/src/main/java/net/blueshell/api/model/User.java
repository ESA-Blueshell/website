package net.blueshell.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.common.enums.MemberType;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.util.TimeUtil;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Data
public class User implements UserDetails, BaseModel<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String username;

    @Column
    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column
    private String prefix;

    @Column
    private String initials;

    @Column
    private String address;

    @Column(name = "house_number")
    private String houseNumber;

    @Column(name = "postal_code")
    private String postalCode;

    @Column
    private String city;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column
    private String email;

    @Column(name = "student_number")
    private String studentNumber;

    @Column(name = "date_of_birth")
    private Timestamp dateOfBirth;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column
    private String discord;

    @Column
    private String steamid;

    @Column
    private boolean newsletter;

    @Column
    private boolean enabled;

    @Column(name = "reset_key")
    private String resetKey;

    @Column(name = "reset_key_valid_until")
    private Timestamp resetKeyValidUntil;

    @Column(name = "reset_type")
    @Enumerated(EnumType.STRING)
    private ResetType resetType;

    @Column(name = "consent_privacy")
    private boolean consentPrivacy;

    @Column(name = "consent_gdpr")
    private boolean consentGdpr;

    @Column
    private String gender;

    @Column
    private String street;

    @Column
    private String country;

    @Column(name = "photo_consent")
    private boolean photoConsent;

    @Column
    private String nationality;

    @OneToOne
    @JoinColumn(name = "profile_picture")
    @JsonIgnore
    private File profilePicture;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "user")
    @JsonIgnore
    private Set<CommitteeMember> committeeMembers;

    @JoinTable(name = "authorities", joinColumns = @JoinColumn(name = "user_id"))
    @ElementCollection(targetClass = Role.class)
    @Enumerated(EnumType.STRING)
    @Column(name = "authority")
    private Set<Role> roles;

    @Column(name = "ehbo")
    private boolean ehbo = false;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "bhv")
    private boolean bhv = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @ToString.Exclude
    @JsonIgnore
    private Set<Contribution> contributions;

    @OneToOne(mappedBy = "user")
    @ToString.Exclude
    private Membership membership;

    @OneToOne
    @JoinColumn(name = "creator_id")
    @JsonIgnore
    private User creator;

    public User() {
        this.createdAt = Timestamp.from(Instant.now());
        addRole(Role.GUEST);
    }

    public User(String username, String password, String firstName, String lastName, String email) {
        this();
        this.username = username;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    @JsonProperty("profilePicture")
    public Long getProfilePictureId() {
        return getProfilePicture() == null ? 0 : getProfilePicture().getId();
    }

    @JsonProperty("committees")
    public Set<Long> getCommitteeIds() {
        Set<Long> set = new HashSet<>();
        if (getCommitteeMembers() == null) {
            return set;
        }
        for (CommitteeMember cm : getCommitteeMembers()) {
            set.add(cm.getCommitteeId());
        }
        return set;
    }

    public Set<Role> getInheritedRoles() {
        return new HashSet<>(getRoles()
                .stream()
                .flatMap(role -> role.getAllInheritedRoles().stream()).toList());
    }

    @JsonProperty("roles")
    public Set<String> getRoleStrings() {
        return getInheritedRoles().stream().map(Objects::toString).collect(Collectors.toSet());
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    @JsonProperty("password")
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id == user.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public boolean hasRole(Role role) {
        return getInheritedRoles().stream().anyMatch(r -> r.matchesRole(role));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        var auths = new HashSet<GrantedAuthority>();
        if (getRoles() == null) {
            return auths;
        }

        getRoles().stream()
                .flatMap(role -> role.getAllInheritedRoles().stream())
                .map(authority -> new SimpleGrantedAuthority(authority.getReprString()))
                .forEach(auths::add);

        return auths;
    }

    public void addRole(Role role) {
        if (getRoles() == null) {
            setRoles(new HashSet<>());
        }
        getRoles().add(role);
    }

    public void removeRole(Role role) {
        getRoles().remove(role);
    }

    public void setEmail(String email) {
        this.email = email.toLowerCase();
    }

    @Override
    public boolean isEnabled() {
        return enabled && (getDeletedAt() == null || !TimeUtil.hasExpired(getDeletedAt()));
    }

    public String getFullName() {
        if (prefix == null || prefix.isEmpty()) {
            return firstName + " " + lastName;
        }
        return firstName + " " + prefix + " " + lastName;
    }

    public String getSantizedFullName() {
        return getFullName().replaceAll("[^a-zA-Z0-9\\s]", "").trim().replaceAll("\\s+", "-");
    }

    public MemberType getMemberType() {
        return getMembership() != null ? getMembership().getMemberType() : null;
    }

    public boolean getIncasso() {
        return getMembership() != null && getMembership().isIncasso();
    }
}
