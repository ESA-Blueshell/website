package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.*;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.committee.CommitteeMember;
import net.blueshell.api.model.contribution.Contribution;
import net.blueshell.api.model.event.EventSignUp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username_deleted_at", columnNames = {"username", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_email_deleted_at", columnNames = {"email", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_student_number_deleted_at", columnNames = {"student_number", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_discord_deleted_at", columnNames = {"discord", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_phone_number_deleted_at", columnNames = {"phone_number", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_address_id_deleted_at", columnNames = {"address_id", "deleted_at"}),
                @UniqueConstraint(name = "uk_users_profile_picture_id_deleted_at", columnNames = {"profile_picture_id", "deleted_at"})
        },
        indexes = {
                @Index(name = "idx_users_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_users_created_at", columnList = "created_at"),
                @Index(name = "idx_users_enabled", columnList = "enabled"),
                @Index(name = "idx_users_newsletter", columnList = "newsletter"),
                @Index(name = "idx_users_last_name", columnList = "last_name"),
                @Index(name = "idx_users_first_name", columnList = "first_name")
        }
)
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@EntityListeners(JpaListener.class)
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
public class User extends BaseModel implements UserDetails {

    @Column(nullable = false)
    @ToString.Include
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "first_name", nullable = false)
    @ToString.Include
    private String firstName;

    @Column(name = "last_name", nullable = false)
    @ToString.Include
    private String lastName;

    @Column
    @ToString.Include
    private String prefix;

    @Column
    @ToString.Include
    private String initials;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "address_id")
    private Address address;

    @Column(name = "address_id", updatable = false, insertable = false)
    @ToString.Include
    private Long addressId;

    @Column(name = "phone_number")
    @ToString.Include
    private String phoneNumber;

    @Column(nullable = false)
    @ToString.Include
    private String email;

    @Column(name = "student_number")
    @ToString.Include
    private String studentNumber;

    @Column(name = "date_of_birth")
    @ToString.Include
    private LocalDate dateOfBirth;

    @Column
    @ToString.Include
    private String discord;

    @Column
    @ToString.Include
    private String steamid;

    @Column(nullable = false)
    @ToString.Include
    private boolean newsletter;

    @Column(nullable = false)
    @ToString.Include
    private boolean enabled;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RecoveryToken> recoveryTokens;

    @Column(name = "consent_privacy")
    @ToString.Include
    private boolean consentPrivacy;

    @Column(name = "consent_gdpr")
    @ToString.Include
    private boolean consentGdpr;

    @Column
    @ToString.Include
    private String gender;

    @Column(name = "photo_consent")
    @ToString.Include
    private boolean photoConsent;

    @Column
    @ToString.Include
    private String nationality;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_picture_id", insertable = false, updatable = false)
    private File profilePicture;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<CommitteeMember> committeeMembers = new HashSet<>();

    @ElementCollection(targetClass = Role.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "authorities", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "authority")
    @ToString.Include
    private Set<Role> roles = EnumSet.of(Role.GUEST);

    @Column(name = "ehbo")
    @ToString.Include
    private boolean ehbo = false;

    @Column(name = "contact_id")
    @ToString.Include
    private Long contactId;

    @Column(name = "bhv")
    @ToString.Include
    private boolean bhv = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Contribution> contributions;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<Membership> memberships;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<EventSignUp> eventSignUps;

    @Column(name = "study")
    @ToString.Include
    private String study;

    @Column(name = "start_study_year")
    @ToString.Include
    private Long startStudyYear;

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
