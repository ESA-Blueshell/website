package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.auth.JWTAuthBase;
import net.blueshell.api.auth.JwtTokenUtil;
import net.blueshell.api.dto.request.JwtRequest;
import net.blueshell.api.dto.response.AuthenticationDTO;
import net.blueshell.api.model.User;
import net.blueshell.api.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController("/auth")
@Tag(name = "Authentication")
@RequiredArgsConstructor
public class AuthenticationController extends JWTAuthBase {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserService users;

    @Value("${app.jwt.expiration}")
    private Long expiration;

    @PostMapping
    @PermitAll
    public AuthenticationDTO authenticate(@Validated @RequestBody JwtRequest authenticationRequest) {
        authenticate(
                authenticationRequest.getUsername(),
                authenticationRequest.getPassword()
        );

        User user = users.findByUsername(authenticationRequest.getUsername());
        String token = jwtTokenUtil.generateToken(user);
        long expirationTime = System.currentTimeMillis() + expiration;

        return new AuthenticationDTO(token,
                user.getId(),
                user.getUsername(),
                expirationTime,
                user.getInheritedRoles()
        );
    }

    private void authenticate(String username, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
    }
}
