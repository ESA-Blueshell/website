package net.blueshell.api.service;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.util.Util;
import net.blueshell.api.controller.filter.UserFilter;
import net.blueshell.api.model.File;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.UserRepository;
import net.blueshell.api.repository.spec.UserSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;

import static net.blueshell.api.common.util.Util.ACTIVATION_KEY_LENGTH;
import static net.blueshell.api.common.util.Util.ACTIVATION_VALID_SECONDS;

@Slf4j
@Service
public class UserService extends BaseModelService<User, UserRepository> implements UserDetailsService {

    @Autowired
    public UserService(UserRepository repository) {
        super(repository);
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

    public User findByResetKey(String resetKey) {
        return repository.findByResetKey(resetKey).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with reset key: %s".formatted(resetKey)));
    }

    public User findByEmail(String email) {
        return repository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with email: %s".formatted(email)));
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

    public Page<User> findByFilter(UserFilter filter, Pageable pageable) {
        if (filter == null) filter = new UserFilter();
        if (pageable == null) pageable = Pageable.unpaged();
        var spec = UserSpecifications.fromFilter(filter, getPrincipal());
        return repository.findAll(spec, pageable);
    }

    public void resetPassword(User user) {
        user.setResetType(ResetType.PASSWORD_RESET);
        user.setResetKey(Util.getRandomCapitalString(ACTIVATION_KEY_LENGTH));
        user.setResetKeyValidUntil(Timestamp.from(Instant.now().plusSeconds(ACTIVATION_VALID_SECONDS)));
        user.setEnabled(false);
    }
}
