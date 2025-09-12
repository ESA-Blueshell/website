package net.blueshell.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.common.enums.MemberType;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.util.TimeUtil;
import net.blueshell.api.util.Util;
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
@EntityListeners(JpaListener.class)
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

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    @ToString.Exclude
    private Address address;

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

    @Column(name = "photo_consent")
    private boolean photoConsent;

    @Column
    private String nationality;

    @OneToOne
    @JoinColumn(name = "profile_picture", insertable = false, updatable = false)
    @JsonIgnore
    private File profilePicture;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<CommitteeMember> committeeMembers;

    @JoinTable(name = "authorities", joinColumns = @JoinColumn(name = "user_id"))
    @ElementCollection(targetClass = Role.class, fetch = FetchType.LAZY)
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
    @JoinColumn(name = "creator_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    private User creator;

    @Column(name = "creator_id")
    private Long creatorId;

    private static final int ACTIVATION_KEY_LENGTH = 15;
    private static final long ACTIVATION_VALID_SECONDS = 3600 * 24 * 3; // 3 days

    public User() {
        this.createdAt = Timestamp.from(Instant.now());
        this.resetKey = Util.getRandomCapitalString(ACTIVATION_KEY_LENGTH);
        this.resetKeyValidUntil = Timestamp.from(Instant.now().plusSeconds(ACTIVATION_VALID_SECONDS));
        this.resetType = hasAuthority(Role.BOARD) ? ResetType.MEMBER_ACTIVATION : ResetType.USER_ACTIVATION;
        addRole(Role.GUEST);
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
            set.add(cm.getCommittee().getId());
        }
        return set;
    }

    public Set<Role> getRoles() {
        return roles == null ? new HashSet<>() : roles;
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
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public boolean hasRole(Role role) {
        return getRoles().stream().anyMatch(r -> r.equals(role));
    }

    public boolean hasAuthority(Role role) {
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
        var roles = getRoles();
        if (roles == null) {
            roles = new HashSet<>();
        }
        roles.add(role);
        setRoles(roles);
    }

    public void removeRole(Role role) {
        var roles = getRoles();
        if (roles == null) {
            return;
        }
        roles.remove(role);
        setRoles(roles);
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

    public MemberType getMemberType() {
        return getMembership() != null ? getMembership().getMemberType() : null;
    }

    public boolean getIncasso() {
        return getMembership() != null && getMembership().isIncasso();
    }
}
