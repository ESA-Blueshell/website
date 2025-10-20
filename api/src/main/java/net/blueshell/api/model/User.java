package net.blueshell.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vladsch.flexmark.ext.ins.Ins;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.util.Util;
import net.blueshell.api.model.committee.CommitteeMember;
import net.blueshell.api.model.contribution.Contribution;
import net.blueshell.api.model.event.EventSignUp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static net.blueshell.api.common.util.Util.ACTIVATION_KEY_LENGTH;
import static net.blueshell.api.common.util.Util.ACTIVATION_VALID_SECONDS;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username_deleted_at", columnNames = {"username", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_email_deleted_at", columnNames = {"email", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_student_number_deleted_at", columnNames = {"student_number", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_discord_deleted_at", columnNames = {"discord", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_phone_number_deleted_at", columnNames = {"phone_number", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_reset_key_deleted_at", columnNames = {"reset_key", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_address_id_deleted_at", columnNames = {"address_id", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_profile_picture_id_deleted_at", columnNames = {"profile_picture_id", "deleted_at"})
        },
        indexes = {
                @Index(name = "idx_users_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_users_created_at", columnList = "created_at"),
                @Index(name = "idx_users_creator_id", columnList = "creator_id"),
                @Index(name = "idx_users_enabled", columnList = "enabled"),
                @Index(name = "idx_users_newsletter", columnList = "newsletter"),
                @Index(name = "idx_users_reset_key", columnList = "reset_key"),
                @Index(name = "idx_users_reset_key_valid_until", columnList = "reset_key_valid_until"),
                @Index(name = "idx_users_last_name", columnList = "last_name"),
                @Index(name = "idx_users_first_name", columnList = "first_name")
        }
)
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Getter
@Setter
@EntityListeners(JpaListener.class)
public class User extends BaseModel implements UserDetails {

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column
    private String prefix;

    @Column
    private String initials;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "address_id")
    private Address address;

    @Column(name = "address_id", updatable = false, insertable = false)
    private Long addressId;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(nullable = false)
    private String email;

    @Column(name = "student_number")
    private String studentNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column
    private String discord;

    @Column
    private String steamid;

    @Column(nullable = false)
    private boolean newsletter;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "reset_key")
    private String resetKey;

    @Column(name = "reset_key_valid_until")
    private Instant resetKeyValidUntil;

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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_picture_id", insertable = false, updatable = false)
    private File profilePicture;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JsonIgnore
    private Set<CommitteeMember> committeeMembers;

    @ElementCollection(targetClass = Role.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "authorities", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "authority")
    private Set<Role> roles = new HashSet<>();

    @Column(name = "ehbo")
    private boolean ehbo = false;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "bhv")
    private boolean bhv = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Contribution> contributions;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<Membership> memberships;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", insertable = false, updatable = false)
    private User creator;
    @Column(name = "creator_id")

    private Long creatorId;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<EventSignUp> eventSignUps;

    @Column(name = "study")
    private String study;

    @Column(name = "start_study_year")
    private Long startStudyYear;

    public User() {
        this.resetKey = Util.getRandomCapitalString(ACTIVATION_KEY_LENGTH);
        this.resetKeyValidUntil = Instant.now().plusSeconds(ACTIVATION_VALID_SECONDS);
        this.resetType = ResetType.USER_ACTIVATION;
    }

    @PrePersist
    private void prePersist() {
        if (roles == null || roles.isEmpty()) {
            roles = new HashSet<>();
            roles.add(Role.GUEST);
        }
        if (this.getId() == null && this.getResetKeyValidUntil() == null) {
            resetType = hasAuthority(Role.BOARD) ? ResetType.MEMBER_ACTIVATION : ResetType.USER_ACTIVATION;
        }
    }

    public Set<CommitteeMember> getCommitteeMembers() {
        return committeeMembers == null ? new HashSet<>() : committeeMembers;
    }

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
        if (roles == null) roles = new HashSet<>();
        return roles;
    }

    public Set<Role> getInheritedRoles() {
        return new HashSet<>(getRoles()
                .stream()
                .flatMap(role -> role.getAllInheritedRoles().stream()).toList());
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
        getRoles().add(role);
    }

    public void removeRole(Role role) {
        getRoles().remove(role);
    }

    public void setEmail(String email) {
        this.email = email.toLowerCase();
    }

    public String getFullName() {
        if (prefix == null || prefix.isEmpty()) {
            return firstName + " " + lastName;
        }
        return firstName + " " + prefix + " " + lastName;
    }
}
