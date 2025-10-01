package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.File;
import net.blueshell.api.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends BaseRepository<User> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<User> findByUsernameAndIdNot(String username, Long id);

    boolean existsByUsernameAndIdNot(String username, Long id);

    Optional<User> findByResetKey(String resetKey);

    boolean existsByResetKey(String resetKey);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByDiscord(String discord);

    boolean existsByDiscordAndIdNot(String discord, Long id);

    Optional<User> findByEmailAndIdNot(String email, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<User> findByPhoneNumberAndIdNot(String phoneNumber, Long id);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);

    List<User> findByMembershipNotNull();

    Optional<User> findByMembershipSignature(File signature);

    Optional<User> findByProfilePicture(File profilePicture);
    @Query("SELECT DISTINCT u FROM User u " +
            "JOIN u.roles r " +
            "WHERE r = 'COMMITTEE'" +
            "AND NOT EXISTS (SELECT 1 FROM CommitteeMember cm WHERE cm.user = u)")
    List<User> findUsersWithRoleWithoutActiveCommittees();

    // Find users who do NOT have COMMITTEE role but ARE active committee members
    @Query("SELECT DISTINCT u FROM User u " +
            "WHERE NOT EXISTS (SELECT 1 FROM u.roles r WHERE r = 'COMMITTEE') " +
            "AND EXISTS (SELECT 1 FROM CommitteeMember cm WHERE cm.user = u)")
    List<User> findUsersWithoutRoleWithActiveCommittees();

    @Query("SELECT DISTINCT u FROM User u " +
            "JOIN u.roles r " +
            "WHERE r = 'MEMBER'" +
            "AND NOT EXISTS (SELECT 1 FROM Membership m WHERE m.user = u AND m.endDate IS NULL)")
    List<User> findUsersWithRoleWithoutActiveMembership();

    @Query("SELECT DISTINCT u FROM User u " +
            "WHERE NOT EXISTS (SELECT 1 FROM u.roles r WHERE r = 'MEMBER') " +
            "AND EXISTS (SELECT 1 FROM Membership m WHERE m.user = u AND m.endDate IS NULL)")
    List<User> findUsersWithoutRoleWithActiveMembership();
}
