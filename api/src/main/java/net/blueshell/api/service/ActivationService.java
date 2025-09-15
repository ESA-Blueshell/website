package net.blueshell.api.service;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.request.MemberActivationRequest;
import net.blueshell.api.dto.request.PasswordResetRequest;
import net.blueshell.api.dto.request.UserActivationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivationService {

    private final UserService users;
    private PasswordEncoder passwordEncoder;

    @Autowired
    public ActivationService(UserService users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public void resetPassword(PasswordResetRequest request) {
        var user = users.findByUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setResetType(null);
        user.setResetKey(null);
        user.setResetKeyValidUntil(null);
        users.update(user);
    }

    public void activate(MemberActivationRequest request) {
        var user = users.findByResetKey(request.getToken());

        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setResetType(null);
        user.setResetKey(null);
        user.setResetKeyValidUntil(null);
        user.setEnabled(true);
        users.update(user);
    }

    public void activate(UserActivationRequest request) {
        var user = users.findByUsername(request.getUsername());

        user.setResetType(null);
        user.setResetKey(null);
        user.setResetKeyValidUntil(null);
        user.setEnabled(true);
        users.update(user);
    }
}