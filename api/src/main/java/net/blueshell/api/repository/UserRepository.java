package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.File;
import net.blueshell.api.model.User;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends BaseRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<User> findByUsernameAndIdNot(String username, Long id);

    boolean existsByUsernameAndIdNot(String username, Long id);

    Optional<User> findByResetKey(String resetKey);

    boolean existsByResetKey(String resetKey);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndIdNot(String email, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<User> findByPhoneNumberAndIdNot(String phoneNumber, Long id);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);

    List<User> findByMembershipNotNull();

    Optional<User> findByMembershipSignature(File signature);

    Optional<User> findByProfilePicture(File profilePicture);
}
