package com.henrique.dashboard.api;

import com.henrique.dashboard.domain.UserAccount;
import com.henrique.dashboard.domain.UserRole;
import com.henrique.dashboard.security.UserManagementService;
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

@RestController
@RequestMapping("/v1/admin/users")
public class UserAdminController {
    private final UserManagementService users;

    public UserAdminController(UserManagementService users) { this.users = users; }

    @GetMapping
    public List<UserResponse> list() { return users.list().stream().map(UserResponse::from).toList(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody CreateUserRequest request) {
        return UserResponse.from(users.create(request.email(), request.password(), request.role()));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable long id, Authentication authentication) {
        users.deactivate(id, authentication.getName());
    }

    public record CreateUserRequest(String email, String password, UserRole role) {
        @Override public String toString() { return "CreateUserRequest[REDACTED]"; }
    }

    public record UserResponse(long id, String email, UserRole role, boolean enabled, Instant createdAt) {
        static UserResponse from(UserAccount user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.isEnabled(), user.getCreatedAt());
        }
    }
}
