package net.blueshell.api.service;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.job.RecoveryEmailEvent;
import net.blueshell.api.controller.filter.UserFilter;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.UserRepository;
import net.blueshell.api.repository.spec.UserSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Slf4j
@Service
public class UserService extends BaseModelService<User, UserRepository> implements UserDetailsService {

    private final ApplicationEventPublisher eventPublisher;
    private final RecoveryService recoveryTokens;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository repository, ApplicationEventPublisher eventPublisher, RecoveryService recoveryTokens, PasswordEncoder passwordEncoder) {
        super(repository);
        this.eventPublisher = eventPublisher;
        this.recoveryTokens = recoveryTokens;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User loadUserByUsername(String username) throws UsernameNotFoundException {
        return findByUsername(username);
    }

    public User findByUsername(String username) {
        return repository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with username: %s".formatted(username)));
    }

    public boolean existsByUsername(String username) {
        return repository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    public boolean existsByPhoneNumber(String phoneNumber) {
        return repository.existsByPhoneNumber(phoneNumber);
    }


    @Transactional
    public User toggleRole(Long id, Role role) {
        var user = self().findById(id);

        if (user.hasRole(role)) {
            user.removeRole(role);
        } else {
            user.addRole(role);
        }
        self().update(user);
        return user;
    }

    @Transactional
    public void addRole(Long id, Role role) {
        var user = self().findById(id);
        if (!user.hasRole(role)) {
            user.addRole(role);
            self().update(user);
        }
    }

    @Transactional
    public void removeRole(Long id, Role role) {
        var user = self().findById(id);
        if (user.hasRole(role)) {
            user.removeRole(role);
            self().update(user);
        }
    }

    @Override
    @Transactional
    public User create(User user) {
        // If BOARD creates a member, username will be set during activation
        if (hasAuthority(Role.BOARD)) {
            user.setUsername(null);
        }

        user = super.create(user);

        if (hasAuthority(Role.BOARD)) {
            var token = recoveryTokens.issue(user, ResetType.MEMBER_ACTIVATION, Duration.ofDays(7));
            eventPublisher.publishEvent(new RecoveryEmailEvent(user.getId(), token, ResetType.MEMBER_ACTIVATION));
        } else {
            var token = recoveryTokens.issue(user, ResetType.USER_ACTIVATION, Duration.ofDays(1));
            eventPublisher.publishEvent(new RecoveryEmailEvent(user.getId(), token, ResetType.USER_ACTIVATION));
        }

        return user;
    }

    public Page<User> findByFilter(UserFilter filter, Pageable pageable) {
        if (filter == null) filter = new UserFilter();
        if (pageable == null) pageable = Pageable.unpaged();
        var spec = UserSpecifications.fromFilter(filter, getPrincipal());
        return repository.findAll(spec, pageable);
    }

    @Transactional
    public void updatePassword(Long userId, String rawPassword) {
        var user = self().findById(userId);
        user.setPassword(passwordEncoder.encode(rawPassword));
        self().update(user);
    }

    @Transactional
    public void activateUser(Long userId) {
        var user = self().findById(userId);
        user.setEnabled(true);
        self().update(user);
    }

    @Transactional
    public void setUsernameAndPassword(Long userId, String username, String rawPassword) {
        var user = self().findById(userId);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        self().update(user);
    }
}
