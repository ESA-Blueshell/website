package net.blueshell.api.service;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.controller.filter.UserFilter;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.UserRepository;
import net.blueshell.api.repository.spec.UserSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class UserService extends BaseModelService<User, UserRepository> implements UserDetailsService {

    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        super(repository);
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
        var user = findById(id);

        if (user.hasRole(role)) {
            user.removeRole(role);
        } else {
            user.addRole(role);
        }
        update(user);
        return user;
    }

    @Transactional
    public void addRole(Long id, Role role) {
        var user = findById(id);
        if (!user.hasRole(role)) {
            user.addRole(role);
            update(user);
        }
    }

    @Transactional
    public void removeRole(Long id, Role role) {
        var user = findById(id);
        if (user.hasRole(role)) {
            user.removeRole(role);
            update(user);
        }
    }

    public Page<User> findByFilter(UserFilter filter, Pageable pageable) {
        if (filter == null) filter = new UserFilter();
        if (pageable == null) pageable = Pageable.unpaged();
        var spec = UserSpecifications.fromFilter(filter, getPrincipal());
        return repository.findAll(spec, pageable);
    }

    @Transactional
    public void updatePassword(Long userId, String rawPassword) {
        var user = findById(userId);
        user.setPassword(passwordEncoder.encode(rawPassword));
        update(user);
    }

    @Transactional
    public void activateUser(Long userId) {
        var user = findById(userId);
        user.setEnabled(true);
        update(user);
    }

    @Transactional
    public void setUsernameAndPassword(Long userId, String username, String rawPassword) {
        var user = findById(userId);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        update(user);
    }

    @Transactional
    public void updateContactId(Long userId, Long contactId) {
        User user = findById(userId);
        user.setContactId(contactId);
        update(user);
    }
}
