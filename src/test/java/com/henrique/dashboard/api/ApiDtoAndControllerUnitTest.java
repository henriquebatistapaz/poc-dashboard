package com.henrique.dashboard.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import dev.henrique.collectorrunner.integration.CollectionSource;
import com.henrique.dashboard.domain.CollectionRun;
import com.henrique.dashboard.domain.UserAccount;
import com.henrique.dashboard.domain.UserRole;
import com.henrique.dashboard.security.UserManagementService;
import com.henrique.dashboard.service.CollectionRunService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class ApiDtoAndControllerUnitTest {

    @Test
    void collectionControllerValidatesRequests() throws Exception {
        CollectionRunService runs = mock(CollectionRunService.class);
        CollectionController controller = new CollectionController(runs);

        // start validations
        assertThatThrownBy(() -> controller.start(null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.start(new CollectionController.CollectionRequest(null, "input")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.start(new CollectionController.CollectionRequest(CollectionSource.RSS, null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.start(new CollectionController.CollectionRequest(CollectionSource.RSS, "   ")))
                .isInstanceOf(ResponseStatusException.class);

        // schedule validations
        assertThatThrownBy(() -> controller.schedule(null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.schedule(new CollectionController.ScheduleRequest(null, "input", Instant.now())))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.schedule(new CollectionController.ScheduleRequest(CollectionSource.RSS, null, Instant.now())))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.schedule(new CollectionController.ScheduleRequest(CollectionSource.RSS, "   ", Instant.now())))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.schedule(new CollectionController.ScheduleRequest(CollectionSource.RSS, "input", null)))
                .isInstanceOf(ResponseStatusException.class);

        // source input limits
        assertThatThrownBy(() -> controller.start(new CollectionController.CollectionRequest(CollectionSource.WIKIPEDIA, "a".repeat(201))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.start(new CollectionController.CollectionRequest(CollectionSource.LOBSTERS, "invalid tag with spaces")))
                .isInstanceOf(ResponseStatusException.class);

        // Lobsters valid hottest tag
        CollectionRun runMock = new CollectionRun("LOBSTERS", Instant.now(), null, "input");
        var idField = CollectionRun.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(runMock, 99L);
        when(runs.start(eq(CollectionSource.LOBSTERS), eq("hottest"))).thenReturn(runMock);

        org.springframework.mock.web.MockHttpServletRequest mockReq = new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(mockReq));
        try {
            var res = controller.start(new CollectionController.CollectionRequest(CollectionSource.LOBSTERS, "hottest"));
            assertThat(res.getStatusCode().value()).isEqualTo(202);
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void dtosSanitizeToString() {
        var authReq = new AuthController.LoginRequest("user@example.com", "supersecret");
        assertThat(authReq.toString()).isEqualTo("LoginRequest[REDACTED]");

        var collReq = new CollectionController.CollectionRequest(CollectionSource.RSS, "https://secret.url");
        assertThat(collReq.toString()).isEqualTo("CollectionRequest[source=RSS, input=REDACTED]");

        Instant now = Instant.now();
        var schedReq = new CollectionController.ScheduleRequest(CollectionSource.RSS, "https://secret.url", now);
        assertThat(schedReq.toString()).isEqualTo("ScheduleRequest[source=RSS, input=REDACTED, scheduledAt=" + now + "]");

        var userReq = new UserAdminController.CreateUserRequest("admin@example.com", "secretpass", UserRole.ADMIN);
        assertThat(userReq.toString()).isEqualTo("CreateUserRequest[REDACTED]");
    }

    @Test
    void userAdminControllerHandlesListAndDeactivateNullAuth() throws Exception {
        UserManagementService users = mock(UserManagementService.class);
        UserAccount account = new UserAccount("admin@example.com", "hash", UserRole.ADMIN);
        var field = UserAccount.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(account, 1L);

        when(users.list()).thenReturn(List.of(account));

        UserAdminController controller = new UserAdminController(users);

        List<UserAdminController.UserResponse> list = controller.list();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).email()).isEqualTo("admin@example.com");

        controller.deactivate(1L, null);
        verify(users).deactivate(1L, "system");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("superadmin@example.com");
        controller.deactivate(2L, auth);
        verify(users).deactivate(2L, "superadmin@example.com");
    }
}
