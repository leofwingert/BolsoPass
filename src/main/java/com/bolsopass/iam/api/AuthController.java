package com.bolsopass.iam.api;

import com.bolsopass.iam.domain.AccountRateLimitedException;
import com.bolsopass.iam.domain.InvalidCredentialsException;
import com.bolsopass.iam.domain.RefreshTokenReuseException;
import com.bolsopass.iam.service.IamService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IamService iamService;

    public record LoginRequest(String email, String password) {
    }

    public record RefreshRequest(String refresh_token) {
    }

    public AuthController(IamService iamService) {
        this.iamService = iamService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            IamService.TokenPairResponse tokenPair = iamService.login(request.email(), request.password());
            return ResponseEntity.ok(Map.of(
                    "access_token", tokenPair.accessToken(),
                    "refresh_token", tokenPair.refreshToken(),
                    "expires_in", tokenPair.expiresIn(),
                    "roles", tokenPair.roles()
            ));
        } catch (AccountRateLimitedException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "RATE_LIMITED", "message", ex.getMessage()));
        } catch (InvalidCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "INVALID_CREDENTIALS", "message", ex.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            IamService.TokenPairResponse tokenPair = iamService.refresh(request.refresh_token());
            return ResponseEntity.ok(Map.of(
                    "access_token", tokenPair.accessToken(),
                    "refresh_token", tokenPair.refreshToken(),
                    "expires_in", tokenPair.expiresIn(),
                    "roles", tokenPair.roles()
            ));
        } catch (RefreshTokenReuseException | InvalidCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "TOKEN_REVOKED_OR_INVALID", "message", ex.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshRequest request) {
        iamService.logout(request.refresh_token());
        return ResponseEntity.noContent().build();
    }
}

