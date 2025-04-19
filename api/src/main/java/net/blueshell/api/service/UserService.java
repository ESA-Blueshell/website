package net.blueshell.api.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.NotFoundException;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.exception.ResourceNotFoundException;
import net.blueshell.api.controller.request.ActivationRequest;
import net.blueshell.api.controller.request.PasswordResetRequest;
import net.blueshell.api.mapper.RequestMapper;
import net.blueshell.api.model.File;
import net.blueshell.api.model.Membership;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.UserRepository;
import net.blueshell.api.service.brevo.ContactService;
import net.blueshell.api.service.brevo.EmailService;
import net.blueshell.api.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sendinblue.ApiException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class UserService extends BaseModelService<User, Long, UserRepository> implements UserDetailsService {

    private static final int ACTIVATION_KEY_LENGTH = 15;
    private static final long USER_ACTIVATION_VALID_SECONDS = 3600 * 24 * 3; // 3 days
    private static final int PASSWORD_RESET_KEY_LENGTH = 15;
    private static final long PASSWORD_RESET_VALID_SECONDS = 3600 * 2; // 2 hours

    private static final int MEMBER_ACTIVATION_KEY_LENGTH = 25;
    private static final long MEMBER_ACTIVATION_VALID_SECONDS = 3600L * 24 * 365 * 100; // 100 years

    private final EmailService emailService;
    private final ContactService contactService;
    private final MembershipService membershipService;
    private final RequestMapper requestMapper;

    @Autowired
    public UserService(UserRepository repository, EmailService emailService, ContactService contactService, MembershipService membershipService, RequestMapper requestMapper) {
        super(repository);
        this.emailService = emailService;
        this.contactService = contactService;
        this.membershipService = membershipService;
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
    public User createUser(User user) throws ApiException {
        contactService.updateContact(user);

        if (hasAuthority(Role.BOARD)) {
            sendMemberActivationEmail(user);
        } else {
            sendUserActivationEmail(user);
        }

        if (user.hasRole(Role.MEMBER)) {
            emailService.sendContributionEmail(user);
        }

        repository.save(user);
        return user;
    }

    @Transactional
    public void updateUser(User user) throws ApiException {
        contactService.updateContact(user);
        repository.save(user);
    }

    private void sendUserActivationEmail(User user) throws ApiException {
        user.setResetKey(Util.getRandomCapitalString(ACTIVATION_KEY_LENGTH));
        user.setResetKeyValidUntil(Timestamp.from(Instant.now().plusSeconds(USER_ACTIVATION_VALID_SECONDS)));
        user.setResetType(ResetType.USER_ACTIVATION);
        emailService.sendUserActivationEmail(user);
    }

    private void sendMemberActivationEmail(User user) throws ApiException {
        user.setResetKey(Util.getRandomCapitalString(MEMBER_ACTIVATION_KEY_LENGTH));
        user.setResetKeyValidUntil(Timestamp.from(Instant.now().plusSeconds(MEMBER_ACTIVATION_VALID_SECONDS)));
        user.setResetType(ResetType.MEMBER_ACTIVATION);
        emailService.sendMemberActivationEmail(user);
    }

    @Transactional
    public void resetPassword(String username) throws ApiException {
        User user = findByUsername(username);

        user.setResetKey(Util.getRandomCapitalString(PASSWORD_RESET_KEY_LENGTH));
        user.setResetKeyValidUntil(Timestamp.from(Instant.now().plusSeconds(PASSWORD_RESET_VALID_SECONDS)));
        user.setResetType(ResetType.PASSWORD_RESET);
        emailService.sendPasswordResetEmail(user);

        repository.save(user);
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
        repository.save(user);
    }

    @Transactional
    public void setPassword(PasswordResetRequest request) {
        User user = findByUsername(request.getUsername());
        requestMapper.fromRequest(request, user);
        repository.save(user);
    }


    @Transactional
    public User updateMembership(Long id, Boolean isMember) {
        User user = findById(id);

        if (isMember) {
            user.addRole(Role.MEMBER);
            if (user.getMembership() == null) {
                Membership membership = new Membership();
                membership.setStartDate(LocalDate.now());
            } else {
                user.getMembership().setEndDate(null);
            }
        } else {
            user.removeRole(Role.MEMBER);
            if (user.getMembership() != null) {
                user.getMembership().setEndDate(LocalDate.now());
            }
        }

        repository.save(user);

        return user;
    }

    @Transactional
    public void delete(User user) {
        user.setDeletedAt(Timestamp.from(Instant.now()));
        repository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = findById(id);
        delete(user);
    }

    @Transactional
    public User toggleRole(Long id, Role role) {
        User user = findById(id);

        if (user.hasRole(role)) {
            user.removeRole(role);
        } else {
            user.addRole(role);
        }
        repository.save(user);
        return user;
    }

    public User getFromBrevo(@NotBlank String email) throws NoSuchFieldException, ApiException, IllegalAccessException {
        return contactService.getUserFromBrevo(email);
    }

    @Transactional
    public void addRole(User user, Role role) {
        if (!user.hasRole(role)) {
            user.addRole(role);
            repository.save(user);
        }
    }

    @Transactional
    public void removeRole(User user, Role role) {
        if (user.hasRole(role)) {
            user.removeRole(role);
            repository.save(user);
        }
    }

    public User findBySignature(File signature) {
        return repository.findByMembershipSignature(signature).orElseThrow(() -> new NotFoundException("User not found for signature: " + signature.getName()));
    }

    public User findByProfilePicture(File profilePicture) {
        return repository.findByProfilePicture(profilePicture).orElseThrow(() -> new NotFoundException("User not found for profile picture: " + profilePicture.getName()));
    }
}
