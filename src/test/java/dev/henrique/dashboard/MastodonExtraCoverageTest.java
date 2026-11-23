package dev.henrique.dashboard;

import dev.henrique.dashboard.api.MastodonIntegrationController;
import dev.henrique.dashboard.domain.MastodonApp;
import dev.henrique.dashboard.domain.MastodonConnection;
import dev.henrique.dashboard.domain.MastodonOauthState;
import dev.henrique.dashboard.repository.CollectedItemRepository;
import dev.henrique.dashboard.repository.MastodonAppRepository;
import dev.henrique.dashboard.repository.MastodonConnectionRepository;
import dev.henrique.dashboard.repository.MastodonOauthStateRepository;
import dev.henrique.dashboard.service.MastodonIntegrationService;
import dev.henrique.dashboard.service.ScheduleInputCipher;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MastodonExtraCoverageTest {

    @Test
    void controllerAndServiceCoverBranches() {
        MastodonIntegrationService mockSvc = mock(MastodonIntegrationService.class);
        MastodonIntegrationController ctrl = new MastodonIntegrationController(mockSvc);
        Authentication auth = new UsernamePasswordAuthenticationToken("user1", "pass");

        when(mockSvc.startConnection("user1", null)).thenReturn(URI.create("https://mastodon.social/oauth"));
        assertNotNull(ctrl.connect(auth, null));

        assertThrows(ResponseStatusException.class, () -> ctrl.search(auth, null));
        assertThrows(ResponseStatusException.class, () -> ctrl.search(auth, new MastodonIntegrationController.SearchRequest(null, 10)));
        assertThrows(ResponseStatusException.class, () -> ctrl.post(auth, null));
        assertThrows(ResponseStatusException.class, () -> ctrl.post(auth, new MastodonIntegrationController.PostRequest(null, "public")));
        assertThrows(ResponseStatusException.class, () -> ctrl.post(auth, new MastodonIntegrationController.PostRequest("   ", "public")));

        MastodonAppRepository apps = mock(MastodonAppRepository.class);
        MastodonOauthStateRepository states = mock(MastodonOauthStateRepository.class);
        MastodonConnectionRepository connections = mock(MastodonConnectionRepository.class);
        ScheduleInputCipher cipher = mock(ScheduleInputCipher.class);
        CollectedItemRepository items = mock(CollectedItemRepository.class);

        MastodonIntegrationService svc = new MastodonIntegrationService(
                apps, states, connections, cipher, items,
                "https://mastodon.social, , ", "http://localhost:8084/", "http://localhost:8085/?tab=1");

        // Cipher not configured branch
        when(cipher.isConfigured()).thenReturn(false);
        assertEquals("http://localhost:8085/?tab=1&mastodon=error", svc.completeConnection("code", "state"));
        assertThrows(ResponseStatusException.class, () -> svc.startConnection("u1", "https://mastodon.social"));

        // Cipher configured branches
        when(cipher.isConfigured()).thenReturn(true);
        assertEquals("http://localhost:8085/?tab=1&mastodon=error", svc.completeConnection("code", null));
        assertEquals("http://localhost:8085/?tab=1&mastodon=error", svc.completeConnection(null, "state"));
        assertEquals("http://localhost:8085/?tab=1&mastodon=error", svc.completeConnection("   ", "state"));

        // RuntimeException inside completeConnection (missing app)
        MastodonOauthState validState = new MastodonOauthState("st1", "u1", "https://mastodon.social", Instant.now().plusSeconds(300));
        when(states.findById("st1")).thenReturn(Optional.of(validState));
        when(apps.findById("https://mastodon.social")).thenReturn(Optional.empty());
        assertEquals("http://localhost:8085/?tab=1&mastodon=error", svc.completeConnection("code1", "st1"));

        // allowedOrigin validation branches
        assertThrows(ResponseStatusException.class, () -> svc.startConnection("u1", null));
        assertThrows(ResponseStatusException.class, () -> svc.startConnection("u1", "   "));
        assertThrows(ResponseStatusException.class, () -> svc.startConnection("u1", "http://[::1"));
        assertThrows(ResponseStatusException.class, () -> svc.startConnection("u1", "https://evil.example"));

        // post branches
        assertThrows(ResponseStatusException.class, () -> svc.post("u1", "hi", "invalid"));
        when(connections.findById("u1")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> svc.post("u1", "hi", "public"));
        assertThrows(ResponseStatusException.class, () -> svc.search("u1", "java", 5));
    }

    @Test
    void passwordHashCliAndPrivateHelpersCoverAllBranches() throws Exception {
        assertThrows(IllegalStateException.class, () -> dev.henrique.dashboard.security.PasswordHashCli.main(new String[0]));

        java.lang.reflect.Method toEntity = MastodonIntegrationService.class.getDeclaredMethod("toEntity", com.henrique.mastodon.MastodonPost.class, Instant.class);
        toEntity.setAccessible(true);

        com.henrique.mastodon.MastodonPost postNullText = new com.henrique.mastodon.MastodonPost("1", "https://m/1", "alice", null, Instant.now());
        dev.henrique.dashboard.domain.CollectedItem item1 = (dev.henrique.dashboard.domain.CollectedItem) toEntity.invoke(null, postNullText, Instant.now());
        assertEquals("@alice", item1.getTitle());

        com.henrique.mastodon.MastodonPost postLongText = new com.henrique.mastodon.MastodonPost("2", "https://m/2", "bob", "x".repeat(1500), Instant.now());
        dev.henrique.dashboard.domain.CollectedItem item2 = (dev.henrique.dashboard.domain.CollectedItem) toEntity.invoke(null, postLongText, Instant.now());
        assertEquals(1000, item2.getTitle().length());
    }

    @Test
    void itemCollectionAndQueryCoverRemainingBranches() {
        dev.henrique.dashboard.service.ItemQueryService qs = mock(dev.henrique.dashboard.service.ItemQueryService.class);
        when(qs.listItems(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(org.springframework.data.domain.Page.empty());
        dev.henrique.dashboard.api.ItemController ic = new dev.henrique.dashboard.api.ItemController(qs, mock(dev.henrique.dashboard.service.ItemRemovalService.class));
        // from != null && to == null branch
        assertNotNull(ic.list(null, null, Instant.now(), null, 0, 10));
        assertNotNull(ic.list(null, null, Instant.now().minusSeconds(10), Instant.now(), 0, 10));
        assertThrows(ResponseStatusException.class, () -> ic.list(null, null, Instant.now(), Instant.now().minusSeconds(10), 0, 10));

        // ItemQueryService.filter with blank q
        var spec = dev.henrique.dashboard.service.ItemQueryService.filter(null, "   ", null, null);
        jakarta.persistence.criteria.Root<dev.henrique.dashboard.domain.CollectedItem> root = mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.CriteriaQuery<?> cq = mock(jakarta.persistence.criteria.CriteriaQuery.class);
        jakarta.persistence.criteria.CriteriaBuilder cb = mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        spec.toPredicate(root, cq, cb);

        try {
            java.lang.reflect.Method vsi = dev.henrique.dashboard.api.CollectionController.class.getDeclaredMethod("validateSourceInput", dev.henrique.collectorrunner.integration.CollectionSource.class, String.class);
            vsi.setAccessible(true);
            vsi.invoke(null, dev.henrique.collectorrunner.integration.CollectionSource.LOBSTERS, "java");
            assertThrows(java.lang.reflect.InvocationTargetException.class, () -> vsi.invoke(null, dev.henrique.collectorrunner.integration.CollectionSource.LOBSTERS, "x".repeat(150)));
            assertThrows(java.lang.reflect.InvocationTargetException.class, () -> vsi.invoke(null, dev.henrique.collectorrunner.integration.CollectionSource.WIKIPEDIA, "abcdef"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
