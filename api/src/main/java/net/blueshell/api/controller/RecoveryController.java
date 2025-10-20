package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.auth.JWTAuthBase;
import net.blueshell.api.dto.recovery.MemberActivationRequest;
import net.blueshell.api.dto.recovery.PasswordResetRequest;
import net.blueshell.api.dto.recovery.UserActivationRequest;
import net.blueshell.api.service.RecoveryService;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@Tag(name = "Recovery")
@RequiredArgsConstructor
@RequestMapping("/recovery")
public class RecoveryController extends JWTAuthBase {

    private final RecoveryService recoveryService;

    @PostMapping("/password/request/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    public void passwordResetRequest(@PathVariable("username") String username) {
        recoveryService.resetPassword(username);
    }

    @PostMapping("/password/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    public void setPassword(@Validated @RequestBody PasswordResetRequest request) {
        recoveryService.setPassword(request.getToken(), request.getPassword());
    }

    @PostMapping("/user/activate/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    public void userActivate(@PathVariable("token") UserActivationRequest request) {
        recoveryService.activateUser(request.getToken());
    }

    @PostMapping("/member/activate/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    public void memberActivate(@Validated @RequestBody MemberActivationRequest request) {
        recoveryService.activateMember(request.getToken(), request.getUsername(), request.getPassword());
    }
}
