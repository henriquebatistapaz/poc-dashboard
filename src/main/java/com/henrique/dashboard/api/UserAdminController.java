package com.henrique.dashboard.api;

import com.henrique.dashboard.domain.UserAccount;
import com.henrique.dashboard.domain.UserRole;
import com.henrique.dashboard.security.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Administration", description = "Endpoints for user management (ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1/admin/users")
public class UserAdminController {
    private final UserManagementService users;

    public UserAdminController(UserManagementService users) { this.users = users; }

    @Operation(summary = "List all registered users", description = "Returns all users and their current roles and status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of users",
                     content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    @GetMapping
    public List<UserResponse> list() { return users.list().stream().map(UserResponse::from).toList(); }

    @Operation(summary = "Create a new user account", description = "Registers a new user with specified role and password")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created successfully",
                     content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid payload or user already exists"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(users.create(request.email(), request.password(), request.role()));
    }

    @Operation(summary = "Deactivate a user account", description = "Disables access for the specified user")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User deactivated successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot deactivate own account"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@Parameter(description = "User ID to deactivate") @PathVariable long id,
                           Authentication authentication) {
        users.deactivate(id, authentication != null ? authentication.getName() : "system");
    }

    public record CreateUserRequest(
        @NotBlank(message = "Email is required")
        @Schema(description = "User email", example = "operator@example.com")
        String email,

        @NotBlank(message = "Password is required")
        @Schema(description = "User password", example = "supersecret")
        String password,

        @NotNull(message = "Role is required")
        @Schema(description = "Role assigned to user", example = "OPERATOR")
        UserRole role
    ) {
        @Override public String toString() { return "CreateUserRequest[REDACTED]"; }
    }

    public record UserResponse(
        @Schema(description = "User unique ID", example = "1")
        long id,
        @Schema(description = "User email", example = "admin@example.com")
        String email,
        @Schema(description = "Role", example = "ADMIN")
        UserRole role,
        @Schema(description = "Account status", example = "true")
        boolean enabled,
        @Schema(description = "Creation timestamp")
        Instant createdAt
    ) {
        static UserResponse from(UserAccount user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.isEnabled(), user.getCreatedAt());
        }
    }
}
