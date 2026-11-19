package com.henrique.dashboard.api;

import com.henrique.dashboard.security.AdminLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Endpoints for user authentication and JWT generation")
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AdminLoginService login;

    @Operation(summary = "Authenticate with email and password", description = "Authenticates an administrator or operator and issues a JWT token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authentication successful",
                     content = @Content(schema = @Schema(implementation = AdminLoginService.LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials or account disabled")
    })
    @PostMapping("/login")
    public AdminLoginService.LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return login.login(request.email(), request.password());
    }

    public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Schema(description = "User email address", example = "admin@example.com")
        String email,

        @NotBlank(message = "Password is required")
        @Schema(description = "Account password", example = "secret")
        String password
    ) {
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }
}
