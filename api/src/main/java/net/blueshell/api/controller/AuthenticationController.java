package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.auth.JWTAuthBase;
import net.blueshell.api.auth.JwtTokenUtil;
import net.blueshell.api.dto.request.JwtRequest;
import net.blueshell.api.dto.request.MemberActivationRequest;
import net.blueshell.api.dto.request.PasswordResetRequest;
import net.blueshell.api.dto.request.UserActivationRequest;
import net.blueshell.api.dto.response.AuthenticationDTO;
import net.blueshell.api.mapper.activation.MemberActivationRequestMapper;
import net.blueshell.api.mapper.activation.PasswordResetRequestMapper;
import net.blueshell.api.mapper.activation.UserActivationRequestMapper;
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
@RestController
@Tag(name = "Authentication")
public class AuthenticationController extends JWTAuthBase {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserService users;
    private final MemberActivationRequestMapper memberActivationMapper;
    private final UserActivationRequestMapper userActivationMapper;
    private final PasswordResetRequestMapper passwordResetMapper;

    @Value("${app.jwt.expiration}")
    private Long expiration;

    public AuthenticationController(
            AuthenticationManager authenticationManager,
            JwtTokenUtil jwtTokenUtil,
            UserService users,
            MemberActivationRequestMapper memberActivationMapper,
            UserActivationRequestMapper userActivationMapper,
            PasswordResetRequestMapper passwordResetMapper
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.users = users;
        this.memberActivationMapper = memberActivationMapper;
        this.userActivationMapper = userActivationMapper;
        this.passwordResetMapper = passwordResetMapper;
    }


    @PostMapping("/auth/user/activate")
    public void userActivate(@Validated @RequestBody UserActivationRequest request) {
        var user = users.findByUsername(request.getUsername());
        userActivationMapper.fromDTO(request, user);
        users.update(user);
    }

    @PostMapping("/auth/member/activate")
    public void memberActivate(@Validated @RequestBody MemberActivationRequest request) {
        var user = users.findByUsername(request.getUsername());
        memberActivationMapper.fromDTO(request, user);
        users.update(user);
    }

    @PostMapping("/auth/password/reset")
    public void resetPassword(@Validated @RequestBody PasswordResetRequest request) {
        var user = users.findByUsername(request.getUsername());
        passwordResetMapper.fromDTO(request, user);
        users.update(user);
    }

    @PostMapping("/auth")
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
