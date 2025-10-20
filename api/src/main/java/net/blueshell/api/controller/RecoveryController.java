package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.auth.JWTAuthBase;
import net.blueshell.api.dto.recovery.MemberActivationRequest;
import net.blueshell.api.dto.recovery.PasswordResetRequest;
import net.blueshell.api.dto.recovery.UserActivationRequest;
import net.blueshell.api.service.RecoveryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@Tag(name = "Recovery")
@RequiredArgsConstructor
@RequestMapping("/recovery")
public class RecoveryController extends JWTAuthBase {

    private final RecoveryService recoveryService;

    @PostMapping("/password/reset/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    public void resetPassword(@PathVariable("username") String username) {
        recoveryService.resetPassword(username);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    public void setPassword(@Valid @RequestBody PasswordResetRequest request) {
        recoveryService.setPassword(request.getToken(), request.getPassword());
    }

    @PostMapping("/user/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    public void userActivate(@Valid @RequestBody UserActivationRequest request) {
        recoveryService.activateUser(request.getToken());
    }

    @PostMapping("/member/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    public void memberActivate(@Valid @RequestBody MemberActivationRequest request) {
        recoveryService.activateMember(request.getToken(), request.getUsername(), request.getPassword());
    }
}
