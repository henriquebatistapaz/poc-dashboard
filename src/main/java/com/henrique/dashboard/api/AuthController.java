package com.henrique.dashboard.api;

import com.henrique.dashboard.security.AdminLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AdminLoginService login;

    @PostMapping("/login")
    public AdminLoginService.LoginResponse login(@RequestBody LoginRequest request) {
        return login.login(request.email(), request.password());
    }

    public record LoginRequest(String email, String password) {
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }
}
