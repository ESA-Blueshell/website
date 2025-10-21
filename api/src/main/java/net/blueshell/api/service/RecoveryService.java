package net.blueshell.api.service;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.job.RecoveryEmailEvent;
import net.blueshell.api.common.event.jpa.PostPersistEvent;
import net.blueshell.api.model.RecoveryToken;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.RecoveryTokenRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Service
public class RecoveryService extends BaseModelService<RecoveryToken, RecoveryTokenRepository> {

    private final SecureRandom random = new SecureRandom();
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher eventPublisher;
    private final UserService users;

    protected RecoveryService(RecoveryTokenRepository repository,
                              PasswordEncoder encoder,
                              ApplicationEventPublisher eventPublisher,
                              UserService users) {
        super(repository);
        this.encoder = encoder;
        this.eventPublisher = eventPublisher;
        this.users = users;
    }

    /**
     * React to user creation: issue appropriate token and send mail.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    @Transactional
    public void onUserCreated(PostPersistEvent<User> event) {
        String rawToken;
        var user = event.getSource();
        log.info("User {} roles {}", user, user.getRoles());
        if (hasAuthority(Role.BOARD)) {
            rawToken = issue(user, ResetType.MEMBER_ACTIVATION, Duration.ofDays(7));
            eventPublisher.publishEvent(new RecoveryEmailEvent(user.getId(), rawToken, ResetType.MEMBER_ACTIVATION));
        } else {
            rawToken = issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1));
            eventPublisher.publishEvent(new RecoveryEmailEvent(user.getId(), rawToken, ResetType.USER_ACTIVATION));
        }
    }

    /**
     * Always 204 to avoid user enumeration.
     */
    @Transactional
    public void resetPassword(String username) {
        try {
            User user = users.findByUsername(username);
            String rawToken = issue(user, ResetType.PASSWORD_RESET, Duration.ofMinutes(30));
            eventPublisher.publishEvent(new RecoveryEmailEvent(user.getId(), rawToken, ResetType.PASSWORD_RESET));
        } catch (ResponseStatusException notFound) {
            // swallow 404
        }
    }

    @Transactional
    public void setPassword(String rawToken, String newPassword) {
        RecoveryToken token = verify(rawToken, ResetType.PASSWORD_RESET);
        users.updatePassword(token.getUser().getId(), newPassword);
        consume(token);
    }

    @Transactional
    public void activateUser(String rawToken) {
        RecoveryToken token = verify(rawToken, ResetType.USER_ACTIVATION);
        users.activateUser(token.getUser().getId());
        consume(token);
    }

    @Transactional
    public void activateMember(String rawToken, String username, String password) {
        RecoveryToken token = verify(rawToken, ResetType.MEMBER_ACTIVATION);
        users.setUsernameAndPassword(token.getUser().getId(), username, password);
        users.activateUser(token.getUser().getId());
        consume(token);
    }

    @Transactional
    public String issue(User user, ResetType type, Duration ttl) {
        // Invalidate all existing active token of this type
        repository.findAllByUser_IdAndTypeAndConsumedAtIsNull(user.getId(), type)
                .forEach(this::delete);

        String selector = randomUrlSafe(16); // 128-bit
        String verifier = randomUrlSafe(32); // 256-bit

        RecoveryToken token = new RecoveryToken();
        token.setUser(user);
        token.setType(type);
        token.setSelector(selector);
        token.setVerifierHash(encoder.encode(verifier));
        token.setExpiresAt(Instant.now().plus(ttl));

        self().create(token);
        return selector + "." + verifier;
    }

    @Transactional
    RecoveryToken verify(String rawToken, ResetType expectedType) {
        String[] parts = rawToken != null ? rawToken.split("\\.", 2) : new String[0];
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw notFound();
        }
        String selector = parts[0];
        String verifier = parts[1];

        RecoveryToken token = repository.findBySelector(selector)
                .filter(t -> t.getType() == expectedType)
                .orElseThrow(this::notFound);

        if (token.isConsumed() || token.isExpired()) {
            throw notFound();
        }
        if (!encoder.matches(verifier, token.getVerifierHash())) {
            throw notFound();
        }
        return token;
    }

    @Transactional
    void consume(RecoveryToken token) {
        token.setConsumedAt(Instant.now());
        self().update(token);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid or expired recovery token");
    }

    private String randomUrlSafe(int numBytes) {
        byte[] bytes = new byte[numBytes];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // in RecoveryService

    @Transactional
    public void resendActivation(String username) {
        try {
            User user = users.findByUsername(username);
            if (user.isEnabled()) return;
            String rawToken = issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1));
            eventPublisher.publishEvent(new RecoveryEmailEvent(user.getId(), rawToken, ResetType.USER_ACTIVATION));
        } catch (ResponseStatusException ignored) {
            // swallow 404 to avoid enumeration
        }
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @Transactional
    public void resendActivationEmail(Long userId) {
        var user = users.findById(userId);
        if (user.isEnabled()) return;

        var recoveryTokens = repository.findAllByUser_IdAndConsumedAtIsNull(userId);
        if (recoveryTokens.stream().anyMatch(r -> r.getType().equals(ResetType.MEMBER_ACTIVATION))) {
            var rawToken = issue(user, ResetType.MEMBER_ACTIVATION, Duration.ofDays(7));
            eventPublisher.publishEvent(new RecoveryEmailEvent(user.getId(), rawToken, ResetType.MEMBER_ACTIVATION));
        } else if (recoveryTokens.stream().anyMatch(r -> r.getType().equals(ResetType.USER_ACTIVATION))) {
            var rawToken = issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1));
            eventPublisher.publishEvent(new RecoveryEmailEvent(user.getId(), rawToken, ResetType.MEMBER_ACTIVATION));
        }
    }
}
