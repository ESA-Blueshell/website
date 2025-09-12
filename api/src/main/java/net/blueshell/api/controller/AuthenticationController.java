package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.auth.JWTAuthBase;
import net.blueshell.api.auth.JwtTokenUtil;
import net.blueshell.api.controller.request.JwtRequest;
import net.blueshell.api.controller.response.JwtResponse;
import net.blueshell.api.model.User;
import net.blueshell.api.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@Tag(name = "Authentication")
public class AuthenticationController extends JWTAuthBase {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserService userService;
    private final PasswordEncoder encoder;

    @Value("${app.jwt.expiration}")
    private Long expiration;

    public AuthenticationController(
            AuthenticationManager authenticationManager,
            JwtTokenUtil jwtTokenUtil,
            UserService userService, PasswordEncoder encoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userService = userService;
        this.encoder = encoder;
    }

    @PostMapping("/auth")
    public JwtResponse authenticate(
            @Valid @RequestBody JwtRequest authenticationRequest) {
        authenticate(
                authenticationRequest.getUsername(),
                authenticationRequest.getPassword()
        );

        User user = userService.findByUsername(authenticationRequest.getUsername());
        String token = jwtTokenUtil.generateToken(user);
        long expirationTime = System.currentTimeMillis() + expiration;

        return new JwtResponse(token,
                user.getId(),
                user.getUsername(),
                expirationTime,
                user.getRoleStrings());
    }

    private void authenticate(String username, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
    }
}
