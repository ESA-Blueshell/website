package net.blueshell.api.service;

import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.exception.ResourceNotFoundException;
import net.blueshell.api.controller.filter.UserFilter;
import net.blueshell.api.model.File;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.UserRepository;
import net.blueshell.api.repository.spec.UserSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public User findBySignature(File signature) {
        return repository.findByMembershipSignature(signature).orElseThrow(() -> new NotFoundException("User not found for signature: " + signature.getName()));
    }

    public User findByProfilePicture(File profilePicture) {
        return repository.findByProfilePicture(profilePicture).orElseThrow(() -> new NotFoundException("User not found for profile picture: " + profilePicture.getName()));
    }

    public Page<User> findByFilter(UserFilter filter, Pageable pageable) {
        if (filter == null) filter = new UserFilter();
        if (pageable == null) pageable = Pageable.unpaged();
        var spec = UserSpecifications.fromFilter(filter, getPrincipal());
        return repository.findAll(spec, pageable);
    }
}
