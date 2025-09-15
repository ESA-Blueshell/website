package net.blueshell.api.service;

import jakarta.ws.rs.NotFoundException;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.exception.ResourceNotFoundException;
import net.blueshell.api.controller.request.ActivationRequest;
import net.blueshell.api.controller.request.PasswordResetRequest;
import net.blueshell.api.mapper.RequestMapper;
import net.blueshell.api.model.File;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.UserRepository;
import net.blueshell.api.service.brevo.BrevoEmailService;
import net.blueshell.api.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class UserService extends BaseModelService<User, Long, UserRepository> implements UserDetailsService {

    private static final int PASSWORD_RESET_KEY_LENGTH = 15;
    private static final long PASSWORD_RESET_VALID_SECONDS = 3600 * 2; // 2 hours

    private final BrevoEmailService emails;
    private final RequestMapper requestMapper;

    @Autowired
    public UserService(UserRepository repository, BrevoEmailService emails, RequestMapper requestMapper) {
        super(repository);
        this.emails = emails;
        this.requestMapper = requestMapper;
    }

    @Override
    public User loadUserByUsername(String username) throws UsernameNotFoundException {
        return findByUsername(username);
    }

    public User findByUsername(String username) {
        return repository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
    }

    public boolean existsByUsername(String username) {
        return repository.existsByUsername(username);
    }

    public User findByResetKey(String resetKey) {
        return repository.findByResetKey(resetKey).orElseThrow(() -> new ResourceNotFoundException("User not found with reset key: " + resetKey));
    }

    public User findByEmail(String email) {
        return repository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    public boolean existsByPhoneNumber(String phoneNumber) {
        return repository.existsByPhoneNumber(phoneNumber);
    }

    public List<User> findByMembershipNotNull() {
        return repository.findByMembershipNotNull();
    }


    @Transactional
    public void resetPassword(String username) {
        User user = findByUsername(username);

        user.setResetKey(Util.getRandomCapitalString(PASSWORD_RESET_KEY_LENGTH));
        user.setResetKeyValidUntil(Timestamp.from(Instant.now().plusSeconds(PASSWORD_RESET_VALID_SECONDS)));
        user.setResetType(ResetType.PASSWORD_RESET);
        emails.passwordReset(user);

        self().update(user);
    }

    @Transactional
    public void activate(ActivationRequest request) {
        User user;
        if (request.getResetType() == ResetType.MEMBER_ACTIVATION) {
            user = findByResetKey(request.getToken());
        } else {
            user = findByUsername(request.getUsername());
        }
        requestMapper.fromRequest(request, user);
        self().update(user);
    }

    @Transactional
    public void setPassword(PasswordResetRequest request) {
        User user = findByUsername(request.getUsername());
        requestMapper.fromRequest(request, user);
        self().update(user);
    }

    @Transactional
    public User toggleRole(Long id, Role role) {
        User user = self().findById(id);

        if (user.hasRole(role)) {
            user.removeRole(role);
        } else {
            user.addRole(role);
        }
        self().update(user);
        return user;
    }

    @Transactional
    public void addRole(User user, Role role) {
        System.out.println("Add role: in user service" + role);
        if (!user.hasRole(role)) {
            user.addRole(role);
            self().update(user);
        }
    }

    @Transactional
    public void removeRole(User user, Role role) {
        if (user.hasRole(role)) {
            user.removeRole(role);
            self().update(user);
        }
    }

    public User findBySignature(File signature) {
        return repository.findByMembershipSignature(signature).orElseThrow(() -> new NotFoundException("User not found for signature: " + signature.getName()));
    }

    public User findByProfilePicture(File profilePicture) {
        return repository.findByProfilePicture(profilePicture).orElseThrow(() -> new NotFoundException("User not found for profile picture: " + profilePicture.getName()));
    }
}
